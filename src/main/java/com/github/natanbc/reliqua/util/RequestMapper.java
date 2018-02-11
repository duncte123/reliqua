package com.github.natanbc.reliqua.util;

import okhttp3.ResponseBody;

import javax.annotation.Nullable;
import java.io.IOException;

@FunctionalInterface
public interface RequestMapper<T> {
    @Nullable
    T apply(@Nullable ResponseBody body) throws IOException;
}
