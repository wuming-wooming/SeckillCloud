package org.sangoria.seckillproduct.controller;

import org.sangoria.common.result.R;
import org.sangoria.seckillproduct.entity.Product;
import org.sangoria.seckillproduct.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 商品不存在 → BizException(PRODUCT_NOT_FOUND) → GlobalExceptionHandler 返回 code=3001 */
    @GetMapping("/{id}")
    public R<Product> detail(@PathVariable Long id) {
        return R.ok(productService.requireById(id));
    }
}