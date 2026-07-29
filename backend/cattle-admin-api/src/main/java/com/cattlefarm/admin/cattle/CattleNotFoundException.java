package com.cattlefarm.admin.cattle;

public class CattleNotFoundException extends RuntimeException {
    public CattleNotFoundException() { super("牛只不存在"); }
}
