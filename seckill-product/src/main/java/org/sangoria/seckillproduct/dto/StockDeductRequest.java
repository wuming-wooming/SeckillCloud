package org.sangoria.seckillproduct.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * T2.6 DB 扣库存请求体。
 */
@Data
public class StockDeductRequest {

    @NotBlank(message = "requestId can't be blank")
    @Size(max = 64, message = "requestId can't be longer than 64")
    private String requestId;

    @NotNull(message = "quantity can't be null")
    @Min(value = 1, message = "quantity can't be less than 1")
    private Integer quantity;
}