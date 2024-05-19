package com.example.excel;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Editor;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.example.util.ConcurrencyUtil;
import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;



/**
 * 自定义表头时封装导出excel的帮助类，主要是用来控制导入导出excel的整个声明周期
 */
@Slf4j
public class ExcelHelperController implements Closeable {

    private CompletableFuture<Void> completableFuture = ConcurrencyUtil.execute(() -> {
    });


    private final String fileName;
    private ExcelWriter excelWriter;

//    @Getter
//    private FileStoreDTO fileStoreDTO;

    private Editor<WriteSheet> writeSheetHook;


    private ExcelWriterBuilder excelWriterBuilder;


    private String filePath;

    private String sheetName = "sheet1";

    private String preSheetName = "sheet1";

    private Editor<ExcelWriter> excelWriterHook;

    private Editor<ExcelWriterBuilder> excelWriterBuilderEditor;

    //是否上传s3服务器
    private final boolean isUpload;

    private final Object[] formatArgs = new Object[5];

    private Editor<ExcelUtil.ExcelHeadOrDataHolder> callback;

    private ExcelUtil.ExcelHeadOrDataHolder excelHeadOrDataHolder;

    private WriteSheet writeSheet;

    private final List<Runnable> runnables = new ArrayList<>();

    private final AtomicInteger workerNum = new AtomicInteger(0);


    private final AtomicInteger introduction = new AtomicInteger(0);

    private final Lock lock = new ReentrantLock(false);

    private int MAX_WORKER_NUM = 8;


    private ExcelHelperController(String fileName, boolean isUpload, int MAX_WORKER_NUM) {
        this.fileName = fileName;
        this.isUpload = isUpload;
        formatArgs[0] = fileName;
        formatArgs[4] = isUpload;
        formatArgs[1] = sheetName;
        this.MAX_WORKER_NUM = MAX_WORKER_NUM;
        this.excelHeadOrDataHolder = new ExcelUtil.ExcelHeadOrDataHolder();
    }

    /**
     * 获取一个本地的ExcelWriter
     *
     * @return
     */
    public static ExcelHelperController writeLocal(String fileName, boolean isUpload) {
        return writeLocal(fileName, isUpload, 8);
    }

    public static ExcelHelperController writeLocal(String fileName, boolean isUpload, int MAX_WORKER_NUM) {
        ExcelHelperController excelHelperController = new ExcelHelperController(fileName, isUpload, MAX_WORKER_NUM);


        excelHelperController.formatArgs[2] = "本地";
        //判断是否包含路径
        if (fileName.contains("/") || fileName.contains("\\")) {
            excelHelperController.filePath = fileName;
        } else {
            excelHelperController.filePath = "/home/forecast/storage";
        }
        excelHelperController.formatArgs[3] = excelHelperController.filePath;
        excelHelperController.excelWriterBuilder = EasyExcel.write(excelHelperController.filePath);
        return excelHelperController;
    }


    public ExcelHelperController ExcelWriterBuilderHook(Editor<ExcelWriterBuilder> excelWriterBuilderEditor) {
        this.excelWriterBuilderEditor = excelWriterBuilderEditor;
        return this;
    }

    /**
     * 获取一个写入response的ExcelWriter
     *
     * @return
     * @throws Exception
     */
    public static ExcelHelperController writeResponse(String fileName) {
        return writeResponse(fileName, 8);
    }

    public static ExcelHelperController writeResponse(String fileName, int MAX_WORKER_NUM) {
        ExcelHelperController excelHelperController = new ExcelHelperController(fileName, false, MAX_WORKER_NUM);

        excelHelperController.formatArgs[2] = "response";
        excelHelperController.formatArgs[3] = "web";

//        excelHelperController.formatArgs[0]= URLDecoder.decode(fileName, "UTF-8")
        excelHelperController.formatArgs[0] = URLUtil.decode(fileName, "UTF-8");

        excelHelperController.excelWriterBuilder = ExcelUtil.writeResponse(fileName);
        return excelHelperController;
    }


    public ExcelHelperController dataHandleHook(Editor<ExcelUtil.ExcelHeadOrDataHolder> callback) {
        this.callback = callback;
        return this;
    }

    /**
     * 写入
     */
    public ExcelHelperController buildFromObj(Collection<?> list, Class<?> clazz) {
        add(() -> {
            Collection<?> head = CollUtil.isEmpty(list) ? Collections.singleton(ReflectUtil.newInstance(clazz)) : list;

            List<List<String>> heads = ExcelUtil.buildHeadFromObj(head);
            List<List<?>> data = ExcelUtil.buildDataFromObj(list);

            this.excelHeadOrDataHolder = new ExcelUtil.ExcelHeadOrDataHolder(heads, data);
        });
        return this;
    }

    private void add(Runnable runnable) {
        lock.lock();
        try {
            introduction.incrementAndGet();
            runnables.add(runnable);
        } catch (Exception exception) {
            log.error("添加任务失败", exception);
        }
    }


    public ExcelHelperController sheetName(String sheetName) {
        this.sheetName = sheetName;
        formatArgs[1] = this.sheetName;
        return this;
    }

    /**
     * 执行写入操作
     * 这一步是必须的，必须在最后调用，可以调用多次，一次代表一次写入
     */
    public ExcelHelperController execute() {
        if (Objects.isNull(excelWriter)) {
            synchronized (this) {
                if (Objects.isNull(excelWriter)) {
                    if (excelWriterBuilderEditor != null)
                        excelWriterBuilder = excelWriterBuilderEditor.edit(excelWriterBuilder);
                    excelWriter = excelWriterBuilder.build();
                }
            }
        }

        lock.lock();
        try {
            runnables.add(this::executeRunnable);
            List<Runnable> newRunnable = new ArrayList<>(runnables);
            runnables.clear();
            int incrementAndGet = workerNum.incrementAndGet();
            completableFuture = completableFuture.thenRunAsync(ConcurrencyUtil.wrapper(() -> {
                //多线程实现
                Stopwatch stopWatch = Stopwatch.createStarted();
                log.info("当前线程：{}，任务集合有：{}", Thread.currentThread().getName(), newRunnable.size());
                newRunnable.forEach(Runnable::run);
                workerNum.decrementAndGet();
                excelHeadOrDataHolder = new ExcelUtil.ExcelHeadOrDataHolder();
                log.info("==================newRunnable，写入耗时：{}ms=========================", stopWatch.elapsed(TimeUnit.MILLISECONDS));
            }), ConcurrencyUtil.getPool());
            if (incrementAndGet >= MAX_WORKER_NUM) {
                log.info("excel写入队列已满，需要等待队列全部执行完毕------");
                completableFuture.join();
                workerNum.compareAndSet(incrementAndGet, 0);
            }
        } catch (Exception exception) {
            log.error("执行任务失败", exception);
        } finally {
            int intro;
            do {
                intro = introduction.get();
                lock.unlock();
            } while ((intro > 0 && introduction.getAndDecrement() != 0));
        }

        return this;
    }

    private void executeRunnable() {
        //回调处理
        callbackHook();
        excelWriter.write(excelHeadOrDataHolder.data, writeSheet);
    }

    private void callbackHook() {
        if (callback != null) {
            excelHeadOrDataHolder = callback.edit(excelHeadOrDataHolder);
        }

        if (!sheetName.equals(preSheetName)) {
            writeSheet = EasyExcel.writerSheet(sheetName).head(excelHeadOrDataHolder.heads).build();
            preSheetName = sheetName;
        }

        //writeSheet hook 使用
        if (writeSheetHook != null) {
            writeSheet = writeSheetHook.edit(writeSheet);
        }

        //excelWriter hook 使用
        if (excelWriterHook != null)
            excelWriter = excelWriterHook.edit(excelWriter);
    }

    /**
     * excelWriter hook
     */
    public ExcelHelperController excelWriterHook(Editor<ExcelWriter> consumer) {
        this.excelWriterHook = consumer;
        return this;
    }

    /**
     * excelWriter hook
     */
    public ExcelHelperController writeSheetHook(Editor<WriteSheet> writeSheetHook) {
        add(() -> this.writeSheetHook = writeSheetHook);
        return this;
    }

    /**
     * 写入
     */
    public void finish() {
        try {
            completableFuture.join();
            if (excelWriter != null) {
                excelWriter.finish();
            }
            if (isUpload) {
//            this.fileStoreDTO = uploadS3();
            }

            String format = "===================执行excel导出任务完成，文件名为：{}，sheetName为：{}，写入方式为：{}，文件路径为：{}，是否上传S3服务器：{}======";
            log.info(StrUtil.format(format, formatArgs));
        } catch (Exception exception) {
            log.error("导出失败了,{}", exception);
        }
    }

    /**
     * 上传
     */
//    @SneakyThrows
//    public FileStoreDTO uploadS3() {
//        return ExcelUtil.uploadS3(filePath, fileName);
//    }
    @Override
    public void close() throws IOException {
        finish();
    }
}
