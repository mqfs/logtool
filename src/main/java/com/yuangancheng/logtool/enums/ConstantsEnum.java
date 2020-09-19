package com.yuangancheng.logtool.enums;

/**
 * @author: Gancheng Yuan
 * @date: 2020/9/17 17:11
 */
public enum ConstantsEnum {
    LOGGER_NAME("loggerName"),
    REQ_ID_NAME("reqIdName"),
    ENABLE_OPEN_CLOSE_SWITCH("enableOpenCloseSwitch"),
    OPEN_CLOSE_KEY("openCloseKey"),
    VAR_OPEN_CLOSE_KEY("varOpenCloseKey");

    private String value;

    ConstantsEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
