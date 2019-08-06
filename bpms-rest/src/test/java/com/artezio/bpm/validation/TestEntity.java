package com.artezio.bpm.validation;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class TestEntity {

    @NotNull
    @NotBlank
    private String notEmptyString;
    @NotNull
    private Integer notNullInteger;
    @Pattern(regexp = "^\\D+$")
    private String nonDigitsString;

    public TestEntity() {
    }

    public TestEntity(String notEmptyString, Integer notNullInteger, String nonDigitsString) {
        this.notEmptyString = notEmptyString;
        this.notNullInteger = notNullInteger;
        this.nonDigitsString = nonDigitsString;
    }

}
