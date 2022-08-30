package com.android1500.gpssetter.module.util

import javax.inject.Qualifier


@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForApplication

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForActivity

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope
