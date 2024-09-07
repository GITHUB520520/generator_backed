package com.project.generate.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.generate.model.dto.generator.GeneratorQueryRequest;
import com.project.generate.model.entity.Generator;
import com.baomidou.mybatisplus.extension.service.IService;
import com.project.generate.model.vo.GeneratorVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author HP
* @description 针对表【generator(代码生成器)】的数据库操作Service
* @createDate 2024-08-25 09:30:01
*/
public interface GeneratorService extends IService<Generator> {

    void validGenerator(Generator generator, boolean b);

    GeneratorVO getGeneratorVO(Generator generator, HttpServletRequest request);

    Wrapper<Generator> getQueryWrapper(GeneratorQueryRequest generatorQueryRequest);

    Page<GeneratorVO> getGeneratorVOPage(Page<Generator> generatorPage, HttpServletRequest request);
}
