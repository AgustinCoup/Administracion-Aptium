package com.example.common.util;

import java.util.List;

public interface FilterStrategy<T, C> {
    List<T> filter(List<T> source, C criteria);
}
