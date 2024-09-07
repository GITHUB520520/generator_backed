package com.project.generate.job;

import javax.annotation.Resource;

import cn.hutool.core.util.StrUtil;
import com.project.generate.constant.FileConstant;
import com.project.generate.manager.CosManager;
import com.project.generate.mapper.GeneratorMapper;
import com.project.generate.model.entity.Generator;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DeleteCosGenerator {

    @Resource
    private CosManager cosManager;

    @Resource
    private GeneratorMapper generatorMapper;

    @XxlJob("clearCosFiles")
    public void clearCosFiles() throws Exception{
        // 删除已被删除的生成器的产物包
        List<Generator> generators = generatorMapper.deleteGenerator();
        List<String> deleteGeneratorDistPath = generators.stream().filter(generator -> StrUtil.isNotBlank(generator.getDistPath())).map(Generator::getDistPath).collect(Collectors.toList());
        List<String> resultDistPath = deleteGeneratorDistPath.stream().map(distPath -> distPath.replaceAll(FileConstant.COS_HOST + "/", "")).collect(Collectors.toList());
        cosManager.deleteObjects(resultDistPath);

        // 删除制作生成器的产物包
        cosManager.deleteDir("/generator_make/");

    }

}
