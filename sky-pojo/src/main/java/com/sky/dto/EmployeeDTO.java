package com.sky.dto;

import lombok.Data;

import javax.validation.constraints.Pattern;
import java.io.Serializable;

@Data
public class EmployeeDTO implements Serializable {

    private Long id;

    private String username;

    private String name;

    private String phone;

    @Pattern(regexp = "^[12]$", message = "性别只能是 1 或 2")
    private String sex;

    private String idNumber;

}
