package com.project.generate.model.dto.generator;

import com.project.maker.meta.Meta;
import lombok.Data;

import java.io.Serializable;

@Data
public class GeneratorMakeRequest implements Serializable {

    /**
     * zipFilePath
     */
    private String zipFilePath;

    /**
     * meta参数
     */
    private Meta meta;

    private static final long serialVersionUID = 1L;
}
