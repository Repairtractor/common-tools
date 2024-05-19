package com.example.excel;

import cn.hutool.core.annotation.Alias;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ModifierUtil;
import cn.hutool.core.util.ReflectUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ExcelUtil {

    /**
     * 获取一个写入response的ExcelWriter
     *
     * @param fileName
     * @return
     * @throws Exception
     */
    public static ExcelWriterBuilder writeResponse(String fileName) {
        // 获取HttpServletResponse对象
        HttpServletResponse response = getResponse();
        // 设置响应头
//        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 这里URLEncoder.encode可以防止中文乱码 当然和easyexcel没有关系
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xls");
        //这里需要设置不关闭流
        ExcelWriterBuilder write = null;
        try {
            write = EasyExcel.write(response.getOutputStream());
        } catch (Exception exception) {
            log.error("导出设置response，excel异常", exception);
        }
        return write;
    }

    /**
     * 获取当前上下文的response
     *
     * @return
     */
    public static HttpServletResponse getResponse() {
        return ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getResponse();
    }

    /**
     * 获取文件名
     *
     * @param namePrefix 文件名前缀
     * @param isNetWork  是否是网络传输
     * @return
     * @throws UnsupportedEncodingException
     */
    @SneakyThrows
    public static String getFileName(String namePrefix, boolean isNetWork) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String nowTime = sdf.format(new Date());
        //这里是为了网络传输乱码问题
        if (isNetWork) {
            return URLEncoder.encode(namePrefix + nowTime, "UTF-8").replaceAll("\\+", "%20");
        }
        return namePrefix + nowTime + ".xls";
    }

    /**
     * 获取一个本地的ExcelWriter
     *
     * @param filePath
     * @return
     */
    public static ExcelWriter writeLocal(String filePath) {
        return EasyExcelFactory.write(filePath).build();
    }


    //在这里将response设置为返回json
    public static void convertResponseToJson() {
        HttpServletResponse response = getResponse();
        response.reset();
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }


    public static class ExcelHeadOrDataHolder {
        public final List<List<String>> heads;
        public final List<List<?>> data;

        public ExcelHeadOrDataHolder(List<List<String>> heads, List<List<?>> data) {
            this.heads = new ArrayList<>();
            this.data = new ArrayList<>();
        }

        public ExcelHeadOrDataHolder() {
            this.heads = new ArrayList<>();
            this.data = new ArrayList<>();
        }

    }


    public static List<List<?>> buildDataFromObj(Collection<?> items) {
        if (CollUtil.isEmpty(items)) {
            return CollUtil.newArrayList();
        }
        return items.stream()
                .map(ExcelUtil::beanFlatMap)
                .map(Map::values)
                .map(ArrayList::new)
                .collect(Collectors.toList());
    }

    public static List<List<String>> buildHeadFromObj(Collection<?> items) {
        if (CollUtil.isEmpty(items)) {
            return CollUtil.newArrayList();
        }
        return items.stream()
                .map(ExcelUtil::beanFlatMap)
                .limit(1)
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .map(CollUtil::newArrayList)
                .collect(Collectors.toList());
    }



    private static Map<String, Object> beanFlatMap(Object item) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : beanMap(item).entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) value;
                map.forEach((k, v) -> data.put(k, v != null ? v : ""));
                continue;
            }
            data.put(key, value != null ? value : "");
        }
        return data;
    }


    /**
     * 通过反射进行bean转map，性能比hutool的好很多
     *
     * @param obj
     * @return
     */
    public static Map<String, Object> beanMap(Object obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (ModifierUtil.hasModifier(field, ModifierUtil.ModifierType.TRANSIENT))
                continue;
            //静态变量不需要
            if (ModifierUtil.hasModifier(field, ModifierUtil.ModifierType.STATIC))
                continue;

            Alias annotation = field.getAnnotation(Alias.class);
            String key = annotation != null ? annotation.value() : field.getName();
            field.setAccessible(true);
            map.put(key, ReflectUtil.getFieldValue(obj, field));
        }
        return map;
    }


//    public static FileStoreDTO uploadS3(String filePath, String fileName) throws IOException {
//        FileStoreUploadCommon bean = SpringUtil.getBean(FileStoreUploadCommon.class);
//        return bean.localFileUploadToS3(filePath, fileName);
//    }


}
