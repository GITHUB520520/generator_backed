package com.project.generate.mapper;

import com.project.generate.model.entity.Generator;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author HP
* @description 针对表【generator(代码生成器)】的数据库操作Mapper
* @createDate 2024-08-25 09:30:00
* @Entity com.project.generate.model.entity.Generator
*/
public interface GeneratorMapper extends BaseMapper<Generator> {

    @Select("select id, distPath from generator where isDelete = 1")
    List<Generator> deleteGenerator();
}




