package com.project.generate.model.vo;

import cn.hutool.json.JSONUtil;
import com.project.maker.meta.Meta;
import com.project.generate.model.entity.Generator;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class GeneratorVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 基础包
     */
    private String basePackage;

    /**
     * 版本
     */
    private String version;

    /**
     * 作者
     */
    private String author;

    /**
     * 标签列表（json 数组）
     */
    private List<String> tags;

    /**
     * 图片
     */
    private String picture;

    /**
     * 文件配置（json字符串）
     */
    private Meta.FileConfigDTO fileConfig;

    /**
     * 模型配置（json字符串）
     */
    private Meta.ModelConfigDTO modelConfig;

    /**
     * 代码生成器产物路径
     */
    private String distPath;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    private UserVO userVO;

    public static Generator voToObj(GeneratorVO generatorVO){
        if (generatorVO == null) return null;
        Generator generator = new Generator();
        BeanUtils.copyProperties(generatorVO, generator);
        generator.setTags(JSONUtil.toJsonStr(generatorVO.getTags()));
        generator.setFileConfig(JSONUtil.toJsonStr(generatorVO.getFileConfig()));
        generator.setModelConfig(JSONUtil.toJsonStr(generatorVO.getModelConfig()));
        return generator;
    }

    public static GeneratorVO objToVo(Generator generator){
        if (generator == null) return null;
        GeneratorVO generatorVO = new GeneratorVO();
        BeanUtils.copyProperties(generator, generatorVO);
        generatorVO.setTags(JSONUtil.toList(generator.getTags(), String.class));
        generatorVO.setFileConfig(JSONUtil.toBean(generator.getFileConfig(), Meta.FileConfigDTO.class));
        generatorVO.setModelConfig(JSONUtil.toBean(generator.getModelConfig(), Meta.ModelConfigDTO.class));
        return generatorVO;
    }

    private static final long serialVersionUID = 1L;

}
