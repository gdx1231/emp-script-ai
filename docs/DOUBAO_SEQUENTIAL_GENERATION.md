# Doubao Seedream 5.0 Sequential Image Generation

## 概述
为豆包 Seedream 5.0 模型添加了组图生成（sequential image generation）功能支持。

## 新增功能

### ImgOptions 新增字段

```java
// 组图生成模式："auto" 启用组图生成
String sequentialImageGeneration;

// 组图生成选项（如 max_images）
JSONObject sequentialImageGenerationOptions;

// 输出格式："png", "jpeg", "webp" 等
String outputFormat;
```

### 便捷方法

```java
// 一键设置组图生成，自动生成 4 张图
ImgOptions opts = new ImgOptions("一组不同季节的同一棵树的插画")
    .model("doubao-seedream-5-0-260128")
    .size("2K")
    .maxSequentialImages(4);
```

## 使用示例

### 基础用法

```java
DoubaoImgProvider provider = new DoubaoImgProvider();
provider.setApiKey("your-api-key");

ImgOptions opts = new ImgOptions("一组不同季节的同一棵树的插画")
    .model("doubao-seedream-5-0-260128")
    .size("2K")
    .sequentialImageGeneration("auto")
    .maxSequentialImages(4)
    .outputFormat("png");

ImgRequest request = new ImgRequest(opts);
ImgResponse response = provider.generate(request);

// 获取生成的图片
for (GeneratedImage img : response.getImages()) {
    System.out.println(img.getUrl());
}
```

### 生成的请求体

```json
{
  "model": "doubao-seedream-5-0-260128",
  "prompt": "一组不同季节的同一棵树的插画",
  "size": "2K",
  "n": 4,
  "sequential_image_generation": "auto",
  "sequential_image_generation_options": {
    "max_images": 4
  },
  "output_format": "png",
  "response_format": "url",
  "watermark": true,
  "stream": false
}
```

## 测试覆盖

- ✅ 基础请求体构建测试
- ✅ 组图生成参数测试
- ✅ 输出格式参数测试
- ✅ 多图引用（refImageUrls）测试
- ✅ 所有 48 个图像提供者测试通过

## 兼容性

- 保持向后兼容：未设置新字段时，请求体不包含这些字段
- 支持豆包 Seedream 3.0/4.0/5.0 全系列模型
- 与 Qwen Image 3.0 和 Wanx 2.7 的参数解析逻辑统一
