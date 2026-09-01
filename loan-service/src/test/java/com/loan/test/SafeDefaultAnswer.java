package com.loan.test;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可序列化安全默认 Answer：将依赖方法调用返回"安全且可被 Jackson 序列化"的值，
 * 避免深桩 mock 被 Result.ok() 包成 data 后 Jackson 序列化 mock 内部对象导致 500。
 *
 * <ul>
 *   <li>String → ""；Boolean → false；数值 → 0；枚举 → 首个常量；</li>
 *   <li>List/Map/Set → 空实例；</li>
 *   <li>其余对象 → 反射创建空实例（多数 POJO/DTO 拥有无参构造），失败则返回 null。</li>
 * </ul>
 */
public class SafeDefaultAnswer implements Answer<Object> {

    @Override
    public Object answer(InvocationOnMock invocation) {
        Class<?> rt = invocation.getMethod().getReturnType();
        if (rt == void.class) {
            return null;
        }
        if (rt == String.class) {
            return "";
        }
        if (rt == Boolean.class || rt == boolean.class) {
            return false;
        }
        if (rt == Integer.class || rt == int.class) {
            return 0;
        }
        if (rt == Long.class || rt == long.class) {
            return 0L;
        }
        if (rt == Double.class || rt == double.class) {
            return 0.0d;
        }
        if (rt == Float.class || rt == float.class) {
            return 0.0f;
        }
        if (List.class.isAssignableFrom(rt)) {
            return new ArrayList<>();
        }
        if (Map.class.isAssignableFrom(rt)) {
            return new HashMap<>();
        }
        if (Set.class.isAssignableFrom(rt)) {
            return new HashSet<>();
        }
        if (rt.isEnum()) {
            Object[] constants = rt.getEnumConstants();
            return constants.length > 0 ? constants[0] : null;
        }
        if (rt.isPrimitive()) {
            return 0;
        }
        try {
            return rt.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }
}
