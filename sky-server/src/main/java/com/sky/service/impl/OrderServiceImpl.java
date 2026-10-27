package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private UserMapper userMapper;

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;

    /**
     * 用户提交订单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种业务异常(地址簿为空，购物车数据为空)
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        Long userId = BaseContext.getCurrentId();

        //查询购物车数据
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if(shoppingCartList == null || shoppingCartList.size() == 0){
            throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        //向订单明细表插入N条数据
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();//订单明细对象
            BeanUtils.copyProperties(cart, orderDetail);//将购物车数据复制到订单明细对象中
            orderDetail.setOrderId(orders.getId());//设置订单明细所属订单的ID
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);


        //清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        //封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // ==================== 本地学习模式 START ====================
        // 学习阶段没有微信支付商户号、商户API私钥、平台证书，无法完成真实的签名和下单，
        // 因此这里跳过真实微信支付，直接返回一份“伪造”的预支付参数，
        // 目的是打通“提交订单 -> 支付 -> 支付成功 -> 修改订单状态”的完整业务链路。
        // 在企业环境中，请恢复下方的真实微信支付代码。
        log.info("【学习模式】跳过真实微信支付下单，返回模拟预支付参数，订单号：{}",
                ordersPaymentDTO.getOrderNumber());

        OrderPaymentVO mockVo = new OrderPaymentVO();
        // nonceStr：随机字符串，正常由微信支付SDK生成
        mockVo.setNonceStr(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        // timeStamp：当前时间戳（秒）
        mockVo.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000));
        // packageStr：真实场景里是微信下单接口返回的 prepay_id=xxxx；这里伪造一个，仅用于联调业务逻辑
        mockVo.setPackageStr("prepay_id=mock_" + ordersPaymentDTO.getOrderNumber());
        mockVo.setSignType("RSA");
        // paySign：真实场景里需要用商户私钥对参数做 RSA 二次签名；这里给个假签名，
        // 小程序端 wx.requestPayment 会调起失败，属正常现象（没有真商户号本来也调不起来），
        // 直接调用下面的模拟回调接口即可完成支付成功流程
        mockVo.setPaySign("MOCK_SIGN_FOR_LEARNING");

        return mockVo;
        // ==================== 本地学习模式 END ====================

        // ==================== 真实微信支付逻辑（企业环境恢复以下代码，并删除上面的模拟代码）====================
        // 当前登录用户id
        // Long userId = BaseContext.getCurrentId();
        // User user = userMapper.getById(userId);
        //
        // //调用微信支付接口，生成预支付交易单
        // JSONObject jsonObject = weChatPayUtil.pay(
        //         ordersPaymentDTO.getOrderNumber(), //商户订单号
        //         new BigDecimal(0.01), //支付金额，单位 元
        //         "苍穹外卖订单", //商品描述
        //         user.getOpenid() //微信用户的openid
        // );
        //
        // if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
        //     throw new OrderBusinessException("该订单已支付");
        // }
        //
        // OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        // vo.setPackageStr(jsonObject.getString("package"));
        //
        // return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 用户分页查询订单
     *
     * @param pageNum
     * @param pageSize
     * @param status
     */
    @Override
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        //设置分页
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        //分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList<>();

        //查询出订单明细，并封装入OrderVO响应
        if (page != null && page.getTotal() > 0){
            for (Orders orders : page) {
                Long orderId = orders.getId();

                //查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);
                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }

    /**
     * 根据id查询订单
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {

        //根据id查询订单
        Orders orders = orderMapper.getById(id);

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 用户取消订单
     * @param id
     */
    @Override
    public void userCancelById(Long id) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        //校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 订单处于待接单状态下取消，需要进行退款
        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            // ==================== 本地学习模式：跳过真实微信退款 ====================
            // 学习模式下支付是模拟的，没有真实交易，无法也不需要进行微信退款；
            // 真实退款需要商户API私钥等证书文件，直接调用会抛 FileNotFoundException。
            // 企业环境中请恢复下方注释掉的退款代码。
            log.info("【学习模式】跳过真实微信退款，订单号：{}", ordersDB.getNumber());

            // 修改订单状态为已取消
            orders.setStatus(Orders.CANCELLED);
        }

        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 用户再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        //根据订单id查询订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(item -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单明细对象的属性复制到购物车对象
            BeanUtils.copyProperties(item, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 条件查询订单
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        //设置分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        //分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        // 需要返回订单菜品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)){
            for (Orders orders : ordersList) {
                //  将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);

                // 将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }

        return orderVOList;
    }

    /**
     * 根据订单id获取订单菜品信息
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        // 根据订单id查询订单菜品信息（订单中的菜品和数量）
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orders.getId());

        // 将订单菜品信息拼接成字符串
        List<String> orderDishesList = orderDetails.stream().map(item -> {
            String orderDish = item.getName() + "*" + item.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将订单菜品信息列表拼接成字符串并返回
        return String.join("", orderDishesList);
    }

    /**
     * 订单统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        // 根据订单id查询订单
        Orders orders = orderMapper.getById(ordersConfirmDTO.getId());

        // 校验订单是否存在
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 校验订单状态是否为待接单
        if (!orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 修改订单状态为待派送
        orders.setStatus(Orders.CONFIRMED);
        orderMapper.update(orders);
    }

    /**
     * 拒绝接单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 根据订单id查询订单
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        // 订单只有存在且状态为2（待接单）才可以拒单
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID){
            // ==================== 本地学习模式：跳过真实微信退款 ====================
            // 支付是模拟的，无真实交易，无需退款；真实退款需要商户证书，会抛 FileNotFoundException。
            // 企业环境中请恢复下方注释掉的退款代码。
            log.info("【学习模式】跳过真实微信退款，订单号：{}", ordersDB.getNumber());
            // String refund = weChatPayUtil.refund(
            //         ordersDB.getNumber(),//商户平台订单号
            //         ordersDB.getNumber(),//商户退款订单号
            //         new BigDecimal(0.01),//退款金额，单位 元
            //         new BigDecimal(0.01));//原订单金额，单位 元
            // log.info("退款：{}", refund);
        }

        // 拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 根据订单id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        // 支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID){
            // ==================== 本地学习模式：跳过真实微信退款 ====================
            // 支付是模拟的，无真实交易，无需退款；真实退款需要商户证书，会抛 FileNotFoundException。
            // 企业环境中请恢复下方注释掉的退款代码。
            log.info("【学习模式】跳过真实微信退款，订单号：{}", ordersDB.getNumber());
            // String refund = weChatPayUtil.refund(
            //         ordersDB.getNumber(),//商户平台订单号
            //         ordersDB.getNumber(),//商户退款订单号
            //         new BigDecimal(0.01),//退款金额，单位 元
            //         new BigDecimal(0.01));//原订单金额，单位 元
            // log.info("申请退款：{}", refund);
        }

        // 管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

    /**
     * 派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
        // 根据订单id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在, 并且状态为3（已接单，即待派送）
        // 注意：只有“待派送”状态的订单才能执行派送操作，原代码误写成了 DELIVERY_IN_PROGRESS（派送中），
        // 导致已接单的订单点派送时永远报“订单状态错误”，状态流转走不下去
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 更新订单状态,状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }

    /**
     * 订单完成
     * @param id
     */
    @Override
    public void complete(Long id) {
        // 根据订单id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在, 并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 更新订单状态,状态转为已完成
        orders.setStatus(Orders.COMPLETED);

        orderMapper.update(orders);
    }

    /**
     * 校验订单地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap<>();
        map.put("address", shopAddress);
        map.put("ak", ak);
        map.put("output", "json");

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3/", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        //注意：百度地理编码 v3 的返回只有一层 result，结构是 {"status":0,"result":{"location":{...}}}
        //不能连续取两次 getJSONObject("result")，否则得到 null，会抛 NullPointerException
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lng + "," + lat;

        map.put("address", address);

        //获取用户地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3/", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("用户地址解析失败");
        }
        //数据解析（同样只有一层 result，见上方说明）
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户经纬度坐标
        String userLngLat = lng + "," + lat;

        map.put("origin", shopLngLat);
        map.put("destination", userLngLat);
        map.put("steps_info", "0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("地址解析失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if (distance > 5000) {
            throw new OrderBusinessException("订单地址超出配送范围");
        }

    }
}
