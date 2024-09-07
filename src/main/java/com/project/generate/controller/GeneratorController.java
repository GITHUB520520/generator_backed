package com.project.generate.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.generate.annotation.AuthCheck;
import com.project.generate.common.*;
import com.project.generate.constant.FileConstant;
import com.project.generate.constant.UserConstant;
import com.project.generate.exception.BusinessException;
import com.project.generate.exception.ThrowUtils;
import com.project.generate.manager.CacheManager;
import com.project.generate.manager.CosManager;
import com.project.generate.model.dto.generator.*;
import com.project.generate.model.entity.Generator;
import com.project.generate.model.entity.User;
import com.project.generate.model.vo.GeneratorVO;
import com.project.generate.service.GeneratorService;
import com.project.generate.service.UserService;
import com.project.maker.generate.GenerateTemplate;
import com.project.maker.generate.MainGenerate;
import com.project.maker.meta.Meta;
import com.project.maker.meta.MetaValidator;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * generator接口
 *
 *
 */
@RestController
@RequestMapping("/generator")
@Slf4j
public class GeneratorController {

    @Resource
    private GeneratorService generatorService;

    @Resource
    private UserService userService;

    @Resource
    private CosManager cosManager;

    @Resource
    private CacheManager cacheManager;
    
    // region 增删改查

    /**
     * 创建generator
     *
     * @param generatorAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addGenerator(@RequestBody GeneratorAddRequest generatorAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(generatorAddRequest == null, ErrorCode.PARAMS_ERROR);
        // todo 在此处将实体类和 DTO 进行转换
        GeneratorVO generatorVO = new GeneratorVO();
        BeanUtils.copyProperties(generatorAddRequest, generatorVO);
        Generator generator = GeneratorVO.voToObj(generatorVO);
        // 数据校验
        generatorService.validGenerator(generator, true);
        
        // todo 填充默认值
        User loginUser = userService.getLoginUser(request);
        generator.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = generatorService.save(generator);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newGeneratorId = generator.getId();
        return ResultUtils.success(newGeneratorId);
    }

    /**
     * 删除generator
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<Boolean> deleteGenerator(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Generator oldGenerator = generatorService.getById(id);
        ThrowUtils.throwIf(oldGenerator == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldGenerator.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 操作数据库
        boolean result = generatorService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新generator（仅管理员可用）
     *
     * @param generatorUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateGenerator(@RequestBody GeneratorUpdateRequest generatorUpdateRequest) {
        if (generatorUpdateRequest == null || generatorUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        GeneratorVO generatorVO = new GeneratorVO();
        BeanUtils.copyProperties(generatorUpdateRequest, generatorVO);
        Generator generator = GeneratorVO.voToObj(generatorVO);

        // 数据校验
        generatorService.validGenerator(generator, false);
        // 判断是否存在
        long id = generatorUpdateRequest.getId();
        Generator oldGenerator = generatorService.getById(id);
        ThrowUtils.throwIf(oldGenerator == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = generatorService.updateById(generator);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取generator（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<GeneratorVO> getGeneratorVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Generator generator = generatorService.getById(id);
        ThrowUtils.throwIf(generator == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(generatorService.getGeneratorVO(generator, request));
    }

    /**
     * 分页获取generator列表（仅管理员可用）
     *
     * @param generatorQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Generator>> listGeneratorByPage(@RequestBody GeneratorQueryRequest generatorQueryRequest) {
        long current = generatorQueryRequest.getCurrent();
        long size = generatorQueryRequest.getPageSize();
        // 查询数据库
        Page<Generator> generatorPage = generatorService.page(new Page<>(current, size),
                generatorService.getQueryWrapper(generatorQueryRequest));
        return ResultUtils.success(generatorPage);
    }

    /**
     * 分页获取generator列表（封装类）
     *
     * @param generatorQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<GeneratorVO>> listGeneratorVOByPage(@RequestBody GeneratorQueryRequest generatorQueryRequest,
                                                               HttpServletRequest request) {
        long current = generatorQueryRequest.getCurrent();
        long size = generatorQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Generator> generatorPage = generatorService.page(new Page<>(current, size),
                generatorService.getQueryWrapper(generatorQueryRequest));
        // 获取封装类
        return ResultUtils.success(generatorService.getGeneratorVOPage(generatorPage, request));
    }

    @PostMapping("/list/page/vo/fast")
    public BaseResponse<Page<GeneratorVO>> listGeneratorVOByPageFast(@RequestBody GeneratorQueryRequest generatorQueryRequest,
                                                                 HttpServletRequest request) {
        long current = generatorQueryRequest.getCurrent();
        long size = generatorQueryRequest.getPageSize();
        String cacheKey = getCacheKey(generatorQueryRequest);
        Object value = cacheManager.get(cacheKey);
        if (value != null) return ResultUtils.success((Page<GeneratorVO>)value);
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        QueryWrapper<Generator> queryWrapper = (QueryWrapper<Generator>) generatorService.getQueryWrapper(generatorQueryRequest);
        queryWrapper.select("id", "name", "description", "tags", "picture", "status", "userId", "createTime", "updateTime");
        Page<Generator> generatorPage = generatorService.page(new Page<>(current, size),
                queryWrapper);
        Page<GeneratorVO> generatorVOPage = generatorService.getGeneratorVOPage(generatorPage, request);
        cacheManager.put(cacheKey, generatorVOPage);
        // 获取封装类
        return ResultUtils.success(generatorVOPage);
    }

    /**
     * 分页获取当前登录用户创建的generator列表
     *
     * @param generatorQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<GeneratorVO>> listMyGeneratorVOByPage(@RequestBody GeneratorQueryRequest generatorQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(generatorQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        generatorQueryRequest.setUserId(loginUser.getId());
        long current = generatorQueryRequest.getCurrent();
        long size = generatorQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Generator> generatorPage = generatorService.page(new Page<>(current, size),
                generatorService.getQueryWrapper(generatorQueryRequest));
        // 获取封装类
        return ResultUtils.success(generatorService.getGeneratorVOPage(generatorPage, request));
    }

    /**
     * 编辑generator（给用户使用）
     *
     * @param generatorEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editGenerator(@RequestBody GeneratorEditRequest generatorEditRequest, HttpServletRequest request) {
        if (generatorEditRequest == null || generatorEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        GeneratorVO generatorVO = new GeneratorVO();
        BeanUtils.copyProperties(generatorEditRequest, generatorVO);
        Generator generator = GeneratorVO.voToObj(generatorVO);
        // 数据校验
        generatorService.validGenerator(generator, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = generatorEditRequest.getId();
        Generator oldGenerator = generatorService.getById(id);
        ThrowUtils.throwIf(oldGenerator == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldGenerator.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = generatorService.updateById(generator);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @GetMapping("/download")
    public void downloadGeneratorById(long id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (id <= 0) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        Generator oldGenerator = generatorService.getById(id);
        ThrowUtils.throwIf(oldGenerator == null, ErrorCode.NOT_FOUND_ERROR);
        String distPath = oldGenerator.getDistPath();
        ThrowUtils.throwIf(StrUtil.isBlank(distPath), ErrorCode.NOT_FOUND_ERROR);
        distPath = distPath.replaceAll(FileConstant.COS_HOST + "/", "");

        COSObjectInputStream cosObjectInput = null;
        try {
            COSObject cosObject = cosManager.getObject(distPath);
            cosObjectInput = cosObject.getObjectContent();
            // 处理下载到的流
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);
            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + distPath);
            // 写入响应
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download error, filepath = " + distPath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            if (cosObjectInput != null) {
                cosObjectInput.close();
            }
        }
    }

    /**
     * 使用代码生成器
     * @param generatorUseRequest
     * @param request
     * @param response
     * @throws IOException
     */
    @PostMapping("/use")
    public void useGenerator(@RequestBody GeneratorUseRequest generatorUseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (generatorUseRequest == null || generatorUseRequest.getId() <= 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        Long id = generatorUseRequest.getId();
        Map<String, Object> dataModel = generatorUseRequest.getDataModel();

        User loginUser = userService.getLoginUser(request);
        Generator oldGenerator = generatorService.getById(id);
        ThrowUtils.throwIf(oldGenerator == null, ErrorCode.NOT_FOUND_ERROR);
        String distPath = oldGenerator.getDistPath();
        ThrowUtils.throwIf(StrUtil.isBlank(distPath), ErrorCode.NOT_FOUND_ERROR);

        //下载产物包
        String projectPath = System.getProperty("user.dir");
        String tempDirPath = projectPath + "/.temp/use/" + id;
        String downloadFilePath = tempDirPath + "/dist.zip";
        distPath = distPath.replaceAll(FileConstant.COS_HOST + "/", "");

        if (!FileUtil.exist(downloadFilePath)) {
            FileUtil.touch(downloadFilePath);
        }
        try {
            cosManager.download(distPath, downloadFilePath);
        } catch (InterruptedException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载生成器失败！");
        }

        File downloadFile = ZipUtil.unzip(downloadFilePath);
        String dataModelPath = tempDirPath + "/dataModel.json";
        String content = JSONUtil.toJsonStr(dataModel);
        FileUtil.writeUtf8String(content, dataModelPath);

        File scriptFile = FileUtil.loopFiles(downloadFile, 2, null).stream().filter(file -> file.isFile() && "generator.bat".equals(file.getName())).findFirst()
                .orElseThrow(RuntimeException::new);

        //添加可执行权限（UNIX和Linux）
//        Set<PosixFilePermission> filePermissions = PosixFilePermissions.fromString("rwxrwxrwx");
//        Path path = scriptFile.toPath();
//        System.out.println(filePermissions);
//        System.out.println(path);
//        if (filePermissions != null && path != null){
//            try {
//                Files.setPosixFilePermissions(path, filePermissions);
//            } catch (IOException e) {
//
//            }
//        }

        //执行脚本
        File parentFile = scriptFile.getParentFile();
        String scriptFileAbsolutePath = scriptFile.getAbsolutePath().replaceAll("\\\\", "/");
        String command[] = new String[]{scriptFileAbsolutePath, "jsonFile", "-f=" + dataModelPath};

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(parentFile);

        try {
            Process process = processBuilder.start();
            // 读取命令的输出
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            // 等待命令执行完成
            int exitCode = process.waitFor();
            if (exitCode != 0) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "执行生成器脚本错误");
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "执行生成器脚本错误");
        }

        String generatedPath = parentFile.getAbsolutePath() + "/generated";
        String resultPath = tempDirPath + "/result.zip";
        File resultFile = ZipUtil.zip(generatedPath, resultPath);

        response.setContentType("application/octet-stream;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + resultFile.getName());
        Files.copy(resultFile.toPath(), response.getOutputStream());

        CompletableFuture.runAsync(() -> {
            FileUtil.del(tempDirPath);
        });
    }

    @PostMapping("/make")
    public void makeGenerator(@RequestBody GeneratorMakeRequest generatorMakeRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (generatorMakeRequest == null) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        String zipFilePath = generatorMakeRequest.getZipFilePath();
        Meta meta = generatorMakeRequest.getMeta();

        ThrowUtils.throwIf(StrUtil.isBlank(zipFilePath), ErrorCode.PARAMS_ERROR);
        //下载产物包
        String projectPath = System.getProperty("user.dir");
        String id = IdUtil.getSnowflakeNextId() + RandomUtil.randomString(4);
        String tempDirPath = projectPath + "/.temp/make/" + id;
        String downloadFilePath = tempDirPath + "/product.zip";
        zipFilePath = zipFilePath.replaceAll(FileConstant.COS_HOST + "/", "");

        if (!FileUtil.exist(downloadFilePath)) {
            FileUtil.touch(downloadFilePath);
        }
        try {
            cosManager.download(zipFilePath, downloadFilePath);
        } catch (InterruptedException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载压缩包失败！");
        }

        File downloadFile = ZipUtil.unzip(downloadFilePath);
        meta.getFileConfig().setSourceRootPath(downloadFile.getAbsolutePath());
        MetaValidator.doValidAndFill(meta);
        String outputPath = String.format("%s/generated/%s", tempDirPath, meta.getName());
        GenerateTemplate generateTemplate = new MainGenerate();
        try {
            generateTemplate.doGenerate(meta, outputPath);
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "制作生成器失败");
        }

        String zipFileName = meta.getName() + ".zip";
        String makeZipFilePath = outputPath + ".zip";

        response.setContentType("application/octet-stream;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + zipFileName);
        Files.copy(Paths.get(makeZipFilePath), response.getOutputStream());

        CompletableFuture.runAsync(() -> {
            FileUtil.del(tempDirPath);
        });
    }
    
    public String getCacheKey(GeneratorQueryRequest generatorQueryRequest){
        String str = JSONUtil.toJsonStr(generatorQueryRequest);
        String cacheKey = DigestUtil.md5Hex(str);
        return "generator:page:" + cacheKey;
    }
        // endregion

}
