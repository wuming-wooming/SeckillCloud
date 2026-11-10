# Jackson 2/3 共存审计（2026-11-XX）

## 正向扫描（应为空）
grep -rn "com.fasterxml.jackson.databind\|com.fasterxml.jackson.core\|new ObjectMapper()" --include="*.java" .
→ 空

## 反向扫描（应有预期内容）
grep -rn "import tools.jackson" --include="*.java" .
```
./seckill-product/src/main/java/org/sangoria/seckillproduct/service/ProductService.java:10:import tools.jackson.databind.json.JsonMapper;
./seckill-product/src/main/java/org/sangoria/seckillproduct/service/SeckillActivityService.java:11:import tools.jackson.core.type.TypeReference;
./seckill-product/src/main/java/org/sangoria/seckillproduct/service/SeckillActivityService.java:12:import tools.jackson.databind.json.JsonMapper;
```

grep -rn "import com.fasterxml.jackson.annotation" --include="*.java" .
```
./seckill-product/src/main/java/org/sangoria/seckillproduct/entity/Product.java:6:import com.fasterxml.jackson.annotation.JsonFormat;
./seckill-product/src/main/java/org/sangoria/seckillproduct/entity/SeckillActivity.java:6:import com.fasterxml.jackson.annotation.JsonFormat;
```

grep -rn "TypeReference" --include="*.java" .
```
./seckill-product/src/main/java/org/sangoria/seckillproduct/service/SeckillActivityService.java:11:import tools.jackson.core.type.TypeReference;
./seckill-product/src/main/java/org/sangoria/seckillproduct/service/SeckillActivityService.java:84:                return jsonMapper.readValue(cached, new TypeReference<List<SeckillActivity>>() {});
```

## 依赖树终态（不可消除的 Jackson 2）
tools.jackson.core:jackson-databind:3.0.2           ← Boot 4 自动配置
com.fasterxml.jackson.core:jackson-annotations:2.20 ← 共享注解
com.fasterxml.jackson.core:jackson-databind:2.20.1  ← jjwt 传递
com.fasterxml.jackson.core:jackson-core:2.20.1      ← nacos 传递