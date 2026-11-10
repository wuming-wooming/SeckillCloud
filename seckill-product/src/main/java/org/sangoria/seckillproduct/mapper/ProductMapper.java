package org.sangoria.seckillproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.sangoria.seckillproduct.entity.Product;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}