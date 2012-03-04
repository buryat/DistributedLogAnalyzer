package com.github.drashid.redis;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import com.google.inject.BindingAnnotation;


@BindingAnnotation
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisChannel { }
