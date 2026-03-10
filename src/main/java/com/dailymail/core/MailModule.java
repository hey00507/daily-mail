package com.dailymail.core;

public interface MailModule {

    String name();

    boolean isEnabled();

    MailContent generate();
}
