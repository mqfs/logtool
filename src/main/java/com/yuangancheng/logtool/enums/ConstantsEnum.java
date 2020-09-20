package com.yuangancheng.logtool.enums;

/**
 * @author: Gancheng Yuan
 * @date: 2020/9/17 17:11
 */
public enum ConstantsEnum {
    LOGGER_NAME("loggerName"),
    REQ_ID_NAME("reqIdName"),
    ENABLE_CLASS_LEVEL_SWITCH("enableClassLevelSwitch"),
    ENABLE_METHOD_LEVEL_SWITCH("enableMethodLevelSwitch"),
    SWITCH_KEY("switchKey"),
    VAR_CLASS_SWITCH_KEY("varClassSwitchKey"),
    VAR_METHOD_SWITCH_KEY("varMethodSwitchKey");

    private String value;

    ConstantsEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
