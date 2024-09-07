package com.project.generate.model.dto.generator;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class GeneratorUseRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 模型参数
     */
    private Map<String, Object> dataModel;

    private static final long serialVersionUID = 1L;
}
