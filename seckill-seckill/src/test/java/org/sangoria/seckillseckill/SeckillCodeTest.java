package org.sangoria.seckillseckill;


import org.junit.jupiter.api.Test;
import org.sangoria.seckillseckill.service.SeckillCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SeckillCodeTest {

    @Test
    void from_shouldMapAllLuaCodes() {
        assertEquals(SeckillCode.DUPLICATE, SeckillCode.from(-1));
        assertEquals(SeckillCode.STOCK_EMPTY, SeckillCode.from(0));
        assertEquals(SeckillCode.SUCCESS, SeckillCode.from(1));
    }

    @Test
    void from_shouldThrowOnUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> SeckillCode.from(2));
        assertThrows(IllegalArgumentException.class, () -> SeckillCode.from(-5));
    }

}
