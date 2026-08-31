> ## Documentation Index
> Fetch the complete documentation index at: https://docs.bigmodel.cn/llms.txt
> Use this file to discover all available pages before exploring further.

# 视频生成(异步)

> 通过调用 [视频模型](/cn/guide/models/video-generation/cogvideox-3) 能力生成视频内容。支持多种视频生成方式，包括文本转视频、图像转视频等。注意此为异步接口，通过 [查询异步结果](/api-reference/%E6%A8%A1%E5%9E%8B-api/%E6%9F%A5%E8%AF%A2%E5%BC%82%E6%AD%A5%E7%BB%93%E6%9E%9C) 获取生成视频结果。点击 **Try it** 按钮可快速试用。



## OpenAPI

````yaml /openapi/openapi.json post /paas/v4/videos/generations
openapi: 3.0.1
info:
  title: ZHIPU AI API
  description: ZHIPU AI 接口提供强大的 AI 能力，包括聊天对话、工具调用和视频生成。
  license:
    name: ZHIPU AI 开发者协议和政策
    url: https://chat.z.ai/legal-agreement/terms-of-service
  version: 1.0.0
  contact:
    name: Z.AI 开发者
    url: https://chat.z.ai/legal-agreement/privacy-policy
    email: user_feedback@z.ai
servers:
  - url: https://open.bigmodel.cn/api/
    description: 开放平台服务
security:
  - bearerAuth: []
tags:
  - name: 模型 API
    description: Chat API
  - name: 工具 API
    description: Web Search API
  - name: Agent API
    description: Agent API
  - name: 文件 API
    description: File API
  - name: 知识库 API
    description: Knowledge API
  - name: 实时 API
    description: Realtime API
  - name: 批处理 API
    description: Batch API
  - name: 助理 API
    description: Assistant API
  - name: 智能体 API（旧）
    description: QingLiu Agent API
paths:
  /paas/v4/videos/generations:
    post:
      tags:
        - 模型 API
      summary: 视频生成(异步)
      description: >-
        通过调用 [视频模型](/cn/guide/models/video-generation/cogvideox-3)
        能力生成视频内容。支持多种视频生成方式，包括文本转视频、图像转视频等。注意此为异步接口，通过
        [查询异步结果](/api-reference/%E6%A8%A1%E5%9E%8B-api/%E6%9F%A5%E8%AF%A2%E5%BC%82%E6%AD%A5%E7%BB%93%E6%9E%9C)
        获取生成视频结果。点击 **Try it** 按钮可快速试用。
      requestBody:
        content:
          application/json:
            schema:
              oneOf:
                - $ref: '#/components/schemas/CogVideoX3Request'
                  title: CogVideoX-3
                - $ref: '#/components/schemas/CogVideoXRequest'
                  title: CogVideoX
                - $ref: '#/components/schemas/ViduText2VideoRequest'
                  title: 'Vidu: Text to Video'
                - $ref: '#/components/schemas/ViduImage2VideoRequest'
                  title: 'Vidu: Image to Video'
                - $ref: '#/components/schemas/ViduFrames2VideoRequest'
                  title: 'Vidu: First & Last Frame to Video'
                - $ref: '#/components/schemas/ViduReference2VideoRequest'
                  title: 'Vidu: Ref to Video'
            examples:
              文生视频示例:
                value:
                  model: cogvideox-3
                  prompt: A cat is playing with a ball.
                  quality: quality
                  with_audio: true
                  size: 1920x1080
                  fps: 30
              图生视频示例:
                value:
                  model: cogvideox-3
                  image_url: >-
                    https://img.iplaysoft.com/wp-content/uploads/2019/free-images/free_stock_photo.jpg
                  prompt: 让画面动起来
                  quality: quality
                  with_audio: true
                  size: 1920x1080
                  fps: 30
              首尾帧生示例:
                value:
                  model: cogvideox-3
                  image_url:
                    - >-
                      https://cdn.bigmodel.cn/markdown/1752547801491cogvideo4.png
                    - >-
                      https://cdn.bigmodel.cn/markdown/1752547813297cogvideo5.png
                  prompt: 让画面动起来
                  quality: quality
                  with_audio: true
                  size: 1920x1080
                  fps: 30
        required: true
      responses:
        '200':
          description: 业务处理成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AsyncResponse'
        default:
          description: 请求失败。
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Error'
components:
  schemas:
    CogVideoX3Request:
      allOf:
        - $ref: '#/components/schemas/BaseVideoGenerationRequest'
        - type: object
          properties:
            model:
              type: string
              description: 要调用的模型编码。
              enum:
                - cogvideox-3
            prompt:
              type: string
              example: A cat is playing with a ball.
              description: 视频的文本描述，字符长度不能超过`512`个字符。`image_url` 和 `prompt` 不能同时为空。
            image_url:
              $ref: '#/components/schemas/ImageUrlInputExtended'
              description: >-
                提供基于其生成内容的图像，如果传入此参数，系统将以该图像为基础进行操作。支持通过`URL`或`Base64`编码传入图片。图片要求如下：图片支持.`png、.jpeg、.jpg`
                格式、图片大小：不超过`5M`。 `image_url` 和 `prompt` 不能同时为空。

                首尾帧：支持输入两张图，上传的第一张图片视作首帧图，第二张图片视作尾帧图，模型将以此参数中传入的图片来生成视频。
            size:
              type: string
              description: 默认值：若不指定，默认生成视频的短边为 `1080`，长边根据原图片比例确认。最高支持 `4K` 分辨率。
              example: 1920x1080
              enum:
                - 1280x720
                - 720x1280
                - 1024x1024
                - 1920x1080
                - 1080x1920
                - 2048x1080
                - 3840x2160
            fps:
              type: integer
              description: 视频帧率（`FPS`），可选值为 `30` 或 `60`。默认值：`30`。
              example: 30
              enum:
                - 30
                - 60
            duration:
              type: integer
              description: 视频持续时长，默认`5`秒，支持`5`、`10`
              example: 5
              enum:
                - 5
                - 10
          required:
            - model
        - $ref: '#/components/schemas/VideoCommonRequest'
    CogVideoXRequest:
      allOf:
        - $ref: '#/components/schemas/BaseVideoGenerationRequest'
        - type: object
          properties:
            model:
              type: string
              description: 要调用的代码。
              enum:
                - cogvideox-2
                - cogvideox-flash
            prompt:
              type: string
              description: 视频的文本描述，最大输入长度为 `512` 个字符。必须提供 `image_url` 或 `prompt`，或两者都提供。
            image_url:
              $ref: '#/components/schemas/ImageUrlInput'
              description: >-
                用于内容生成的基础图像。如果提供，系统将基于此图像进行操作。支持 `URL` 或 `Base64` 编码图像。图像要求：支持
                `.png、.jpeg、.jpg` 格式；图像大小不超过 `5M`。必须提供 `image_url` 或
                `prompt`，或两者都提供。
            size:
              type: string
              description: >-
                默认：如果未指定，生成视频的短边默认为 `1080`，长边根据原始图像比例缩放。支持最高 `4K`
                分辨率。分辨率选项：`720x480`、`1024x1024`、`1280x960`、`960x1280`、`1920x1080`、`1080x1920`、`2048x1080`、`3840x2160`。
              example: 1920x1080
              enum:
                - 720x480
                - 1024x1024
                - 1280x960
                - 960x1280
                - 1920x1080
                - 1080x1920
                - 2048x1080
                - 3840x2160
            fps:
              type: integer
              description: 视频帧率（`FPS`），可选值为 `30` 或 `60`。默认：`30`。
              example: 30
              enum:
                - 30
                - 60
          required:
            - model
        - $ref: '#/components/schemas/VideoCommonRequest'
    ViduText2VideoRequest:
      allOf:
        - type: object
          properties:
            model:
              type: string
              description: 要调用的代码。
              enum:
                - viduq1-text
            prompt:
              type: string
              description: 视频的文本描述，最大输入长度为 `512` 个字符。
            style:
              type: string
              description: |-
                风格
                默认：`general`
                可选值：`general`、`anime`
                `general`：通用风格，可以使用提示词控制定义风格。
                `anime`：动漫风格，针对动漫特定视觉效果进行优化。可以使用不同的动漫主题提示词控制风格。
              enum:
                - general
                - anime
            duration:
              type: integer
              description: |-
                视频时长参数。
                默认：`5`，可选：`5`。
              example: 5
              enum:
                - 5
            aspect_ratio:
              type: string
              description: |-
                宽高比
                默认：`16:9`，可选值：`16:9`、`9:16`、`1:1`
              example: '16:9'
              enum:
                - '16:9'
                - '9:16'
                - '1:1'
            size:
              type: string
              description: |-
                分辨率参数
                默认：`1920x1080`，可选：`1920x1080`
              example: 1920x1080
              enum:
                - 1920x1080
            movement_amplitude:
              type: string
              description: |-
                运动幅度
                默认：`auto`，可选值：`auto`、`small`、`medium`、`large`
              example: auto
              enum:
                - auto
                - small
                - medium
                - large
          required:
            - model
            - prompt
        - $ref: '#/components/schemas/VideoCommonRequest'
    ViduImage2VideoRequest:
      allOf:
        - type: object
          properties:
            model:
              type: string
              description: 要调用的代码。
              enum:
                - viduq1-image
                - vidu2-image
            prompt:
              type: string
              description: 视频的文本描述，最大输入长度为 `512` 个字符。必须提供 `image_url` 或 `prompt`，或两者都提供。
            image_url:
              $ref: '#/components/schemas/ImageUrlInput'
              description: >-
                系统将使用此参数中提供的图像作为第一帧来生成视频。

                仅支持 `1` 张图像。

                支持的格式：`png`、`jpeg`、`jpg`、`webp`。

                图像宽高比必须小于 `1:4` 或 `4:1`。

                图像文件大小不得超过 `50MB`。

                注意：`Base64` 解码后，字节长度必须小于
                `50MB`，编码必须包含适当的内容类型字符串（例如，`data:image/png;base64,{base64_encode}`）。
            duration:
              oneOf:
                - title: viduq1-image
                  type: integer
                  description: |-
                    视频时长参数。
                    默认：`5`，可选：`5`。
                  example: 5
                  enum:
                    - 5
                - title: viduq2-image
                  type: integer
                  description: |-
                    视频时长参数。
                    默认：`4`，可选：`4`。
                  example: 4
                  enum:
                    - 4
            size:
              oneOf:
                - title: viduq1-image
                  type: string
                  description: |-
                    分辨率参数
                    默认：`1920x1080`，可选：`1920x1080`
                  example: 1920x1080
                  enum:
                    - 1920x1080
                - title: viduq2-image
                  type: string
                  description: |-
                    分辨率参数
                    默认：`1280x720`，可选：`1280x720`
                  example: 1280x720
                  default: 1280x720
                  enum:
                    - 1280x720
            movement_amplitude:
              type: string
              description: |-
                运动幅度
                默认：`auto`，可选值：`auto`、`small`、`medium`、`large`
              example: auto
              enum:
                - auto
                - small
                - medium
                - large
            with_audio:
              type: boolean
              description: 为生成的视频添加背景音乐，仅当最终生成的视频时长为 `4`秒 时支持。
          required:
            - model
        - $ref: '#/components/schemas/VideoCommonRequest'
    ViduFrames2VideoRequest:
      allOf:
        - type: object
          properties:
            model:
              type: string
              description: 要调用的代码。
              enum:
                - viduq1-start-end
                - vidu2-start-end
            prompt:
              type: string
              description: 视频的文本描述，最大输入长度为 `512` 个字符。必须提供 `image_url` 或 `prompt`，或两者都提供。
            image_url:
              type: array
              description: >-
                图像

                支持输入两张图像：第一张上传的图像将被视为第一帧，第二张图像作为最后一帧。模型将使用此参数中提供的图像来生成视频。

                两张输入图像（第一帧和最后一帧）的分辨率必须相似，第一帧分辨率与最后一帧分辨率的比例应在 `0.8–1.25`
                范围内。此外，图像宽高比必须小于 `1:4` 或 `4:1`。

                支持图像 `URL` 或 `Base64` 编码的图像（确保可访问性；建议使用图像 URL）。

                支持的格式：`png`、`jpeg`、`.jpg`、`webp`。

                图像文件大小不得超过 `50 MB`。

                注意：`Base64` 解码后，字节长度必须小于 `50MB`，编码必须包含适当的内容类型字符串，例如
                `data:image/png;base64,{base64_encode}`。
              items:
                type: string
                minLength: 1
              minItems: 1
              maxItems: 2
            duration:
              oneOf:
                - title: viduq1-start-end
                  type: integer
                  description: |-
                    视频时长参数。
                    默认：`5`，可选：`5`。
                  example: 5
                  enum:
                    - 5
                - title: vidu2-start-end
                  type: integer
                  description: |-
                    视频时长参数。
                    默认：`4`，可选：`4`。
                  example: 4
                  enum:
                    - 4
            size:
              oneOf:
                - title: viduq1-start-end
                  type: string
                  description: |-
                    分辨率参数
                    默认：`1920x1080`，可选：`1920x1080`
                  example: 1920x1080
                  enum:
                    - 1920x1080
                - title: vidu2-start-end
                  type: string
                  description: |-
                    分辨率参数
                    默认：`1280x720`，可选：`1280x720`, `480x360`
                  example: 1280x720
                  default: 1280x720
                  enum:
                    - 1280x720
                    - 480x360
            movement_amplitude:
              type: string
              description: |-
                运动幅度
                默认：`auto`，可选值：`auto`、`small`、`medium`、`large`
              example: auto
              enum:
                - auto
                - small
                - medium
                - large
            with_audio:
              type: boolean
              description: 为生成的视频添加背景音乐。
          required:
            - model
        - $ref: '#/components/schemas/VideoCommonRequest'
    ViduReference2VideoRequest:
      allOf:
        - type: object
          properties:
            model:
              type: string
              description: 要调用的代码。
              enum:
                - vidu2-reference
            prompt:
              type: string
              description: 视频的文本描述，最大输入长度为 `512` 个字符。必须提供 `image_url` 或 `prompt`，或两者都提供。
            image_url:
              type: array
              description: >-
                图像参考

                支持输入 `1` 到 `3` 张图像。模型将使用此参数中提供的图像主题作为参考，生成具有一致主体的视频。

                1. 支持图像 `URL` 或 `Base64` 编码的图像（确保可访问性；建议优先使用图像 URL）。

                2. 支持的格式：`png`、`jpeg`、`.jpg`、`webp`。

                3. 图像分辨率不得小于 `128x128`，宽高比必须小于 `1:4` 或 `4:1`。

                4. 图像文件大小不得超过 `50 MB`。

                5. 注意：`Base64` 解码后，字节长度必须小于 `50MB`，编码必须包含适当的内容类型字符串，例如
                `data:image/png;base64,{base64_encode}`。
              items:
                type: string
                minLength: 1
              minItems: 1
              maxItems: 3
            duration:
              title: vidu2-reference
              type: integer
              description: |-
                视频时长参数。
                默认：`4`，可选：`4`。
              example: 4
              enum:
                - 4
            aspect_ratio:
              type: string
              description: |-
                宽高比
                默认：`16:9`，可选值：`16:9`、`9:16`、`1:1`
              example: '16:9'
              enum:
                - '16:9'
                - '9:16'
                - '1:1'
            size:
              title: 'vidu2-reference '
              type: string
              description: |-
                分辨率参数
                默认：`1280x720`，可选：`1280x720`
              example: 1280x720
              enum:
                - 1280x720
            movement_amplitude:
              type: string
              description: |-
                运动幅度
                默认：`auto`，可选值：`auto`、`small`、`medium`、`large`
              example: auto
              enum:
                - auto
                - small
                - medium
                - large
            with_audio:
              type: boolean
              description: 为生成的视频添加背景音乐。
          required:
            - model
        - $ref: '#/components/schemas/VideoCommonRequest'
    AsyncResponse:
      type: object
      properties:
        model:
          description: 此次调用使用的名称。
          type: string
        id:
          description: 生成的任务`ID`，调用请求结果接口时使用此`ID`。
          type: string
        request_id:
          description: 用户在客户端请求期间提交的任务编号或平台生成的任务编号。
          type: string
        task_status:
          description: 处理状态，`PROCESSING (处理中)`、`SUCCESS (成功)`、`FAIL (失败)`。结果需要通过查询获取。
          type: string
    Error:
      type: object
      properties:
        error:
          required:
            - code
            - message
          type: object
          properties:
            code:
              type: string
            message:
              type: string
    BaseVideoGenerationRequest:
      type: object
      properties:
        prompt:
          type: string
          description: 视频的文本描述
        quality:
          type: string
          description: 输出模式，默认为 `speed`。 `quality`：质量优先，生成质量高。 `speed`：速度优先，生成时间更快，质量相对稍低。
          example: speed
          enum:
            - speed
            - quality
        with_audio:
          type: boolean
          description: 是否生成 `AI` 音效。默认值：`False` （不生成音效）。
          example: false
        watermark_enabled:
          type: boolean
          description: |-
            控制`AI`生成图片时是否添加水印。
             - `true`: 默认启用`AI`生成的显式水印及隐式数字水印，符合政策要求。
             - `false`: 关闭所有水印，仅允许已签署免责声明的客户使用，签署路径：个人中心-安全管理-去水印管理
          example: true
    ImageUrlInputExtended:
      oneOf:
        - $ref: '#/components/schemas/ImageUrlInput'
        - title: Image URLs Array (First & Last Frame)
          type: array
          items:
            type: string
            format: uri
          minItems: 2
          maxItems: 2
          example:
            - https://example.com/first_frame.png
            - https://example.com/last_frame.png
    VideoCommonRequest:
      type: object
      properties:
        request_id:
          type: string
          description: 由客户端提供，必须唯一；用于区分每个请求的唯一标识符。如果客户端未提供，平台将默认生成一个。
        user_id:
          type: string
          description: >-
            终端用户的唯一 `ID`，协助平台干预终端用户违规、生成非法或不当信息或其他滥用行为。`ID` 长度要求：最少 `6` 个字符，最多
            `128` 个字符。
    ImageUrlInput:
      oneOf:
        - title: Image URL
          type: string
          format: uri
          example: https://example.com/image.png
        - title: Base64 Encoded Image
          type: string
          format: byte
          example: data:image/png;base64, XXX
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      description: >-
        标准的 HTTP Bearer 认证方式，在 [API
        Keys](https://bigmodel.cn/usercenter/proj-mgmt/apikeys) 页面获取密钥。

````

> ## Documentation Index
> Fetch the complete documentation index at: https://docs.bigmodel.cn/llms.txt
> Use this file to discover all available pages before exploring further.

# 查询异步结果

> 查询对话补全和视频生成异步请求的处理结果和状态。点击 **Try it** 按钮可快速试用。



## OpenAPI

````yaml /openapi/openapi.json get /paas/v4/async-result/{id}
openapi: 3.0.1
info:
  title: ZHIPU AI API
  description: ZHIPU AI 接口提供强大的 AI 能力，包括聊天对话、工具调用和视频生成。
  license:
    name: ZHIPU AI 开发者协议和政策
    url: https://chat.z.ai/legal-agreement/terms-of-service
  version: 1.0.0
  contact:
    name: Z.AI 开发者
    url: https://chat.z.ai/legal-agreement/privacy-policy
    email: user_feedback@z.ai
servers:
  - url: https://open.bigmodel.cn/api/
    description: 开放平台服务
security:
  - bearerAuth: []
tags:
  - name: 模型 API
    description: Chat API
  - name: 工具 API
    description: Web Search API
  - name: Agent API
    description: Agent API
  - name: 文件 API
    description: File API
  - name: 知识库 API
    description: Knowledge API
  - name: 实时 API
    description: Realtime API
  - name: 批处理 API
    description: Batch API
  - name: 助理 API
    description: Assistant API
  - name: 智能体 API（旧）
    description: QingLiu Agent API
paths:
  /paas/v4/async-result/{id}:
    get:
      tags:
        - 模型 API
      summary: 查询异步结果
      description: 查询对话补全和视频生成异步请求的处理结果和状态。点击 **Try it** 按钮可快速试用。
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            description: 任务 ID。
      responses:
        '200':
          description: 业务处理成功
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: '#/components/schemas/ChatCompletionResponse'
                    title: 对话补全
                  - $ref: '#/components/schemas/AsyncVideoGenerationResponse'
                    title: 视频生成
                  - $ref: '#/components/schemas/AsyncImageGenerationResponse'
                    title: 图像生成
            text/event-stream:
              schema:
                $ref: '#/components/schemas/ChatCompletionChunk'
        default:
          description: 请求失败。
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Error'
components:
  schemas:
    ChatCompletionResponse:
      type: object
      properties:
        id:
          description: 任务 `ID`
          type: string
        request_id:
          description: 请求 `ID`
          type: string
        created:
          description: 请求创建时间，`Unix` 时间戳（秒）
          type: integer
        model:
          description: 模型名称
          type: string
        choices:
          type: array
          description: 模型响应列表
          items:
            type: object
            properties:
              index:
                type: integer
                description: 结果索引
              message:
                $ref: '#/components/schemas/ChatCompletionResponseMessage'
              finish_reason:
                type: string
                description: >-
                  推理终止原因。'stop’表示自然结束或触发stop词，'tool_calls’表示模型命中函数，'length’表示达到token长度限制，'sensitive’表示内容被安全审核接口拦截（用户应判断并决定是否撤回公开内容），'network_error’表示模型推理异常，'model_context_window_exceeded'表示超出模型上下文窗口。
        usage:
          type: object
          description: 调用结束时返回的 `Token` 使用统计。
          properties:
            prompt_tokens:
              type: number
              description: 用户输入的 `Token` 数量。
            completion_tokens:
              type: number
              description: 输出的 `Token` 数量
            prompt_tokens_details:
              type: object
              properties:
                cached_tokens:
                  type: number
                  description: 命中的缓存 `Token` 数量
            total_tokens:
              type: integer
              description: '`Token` 总数，对于 `glm-4-voice` 模型，`1`秒音频=`12.5 Tokens`，向上取整'
        video_result:
          type: array
          description: 视频生成结果。
          items:
            type: object
            properties:
              url:
                type: string
                description: 视频链接。
              cover_image_url:
                type: string
                description: 视频封面链接。
        web_search:
          type: array
          description: 返回与网页搜索相关的信息，使用`WebSearchToolSchema`时返回
          items:
            type: object
            properties:
              icon:
                type: string
                description: 来源网站的图标
              title:
                type: string
                description: 搜索结果的标题
              link:
                type: string
                description: 搜索结果的网页链接
              media:
                type: string
                description: 搜索结果网页的媒体来源名称
              publish_date:
                type: string
                description: 网站发布时间
              content:
                type: string
                description: 搜索结果网页引用的文本内容
              refer:
                type: string
                description: 角标序号
        content_filter:
          type: array
          description: 返回内容安全的相关信息
          items:
            type: object
            properties:
              role:
                type: string
                description: >-
                  安全生效环节，包括 `role = assistant` 模型推理，`role = user` 用户输入，`role =
                  history` 历史上下文
              level:
                type: integer
                description: 严重程度 `level 0-3`，`level 0`表示最严重，`3`表示轻微
    AsyncVideoGenerationResponse:
      type: object
      properties:
        model:
          type: string
          description: 模型名称。
        task_status:
          type: string
          description: 任务处理状态，`PROCESSING`（处理中），`SUCCESS`（成功），`FAIL`（失败） 注：处理中状态需通过查询获取结果
        video_result:
          type: array
          description: 数组，包含生成的视频`URL`。
          items:
            type: object
            properties:
              url:
                type: string
                description: 视频链接。
              cover_image_url:
                type: string
                description: 视频封面链接。
        request_id:
          type: string
          description: 标识此次请求的唯一`ID`，可由用户在客户端请求时提交或平台自动生成。
    AsyncImageGenerationResponse:
      type: object
      properties:
        model:
          type: string
          description: 模型名称。
        task_status:
          type: string
          description: 任务处理状态，`PROCESSING`（处理中），`SUCCESS`（成功），`FAIL`（失败） 注：处理中状态需通过查询获取结果
        image_result:
          type: array
          description: 数组，包含生成的图片`URL`。
          items:
            type: object
            properties:
              url:
                type: string
                description: 图片链接。图片的临时链接有效期为`30`天，请及时转存图片。
        request_id:
          type: string
          description: 标识此次请求的唯一`ID`，可由用户在客户端请求时提交或平台自动生成。
    ChatCompletionChunk:
      type: object
      properties:
        id:
          type: string
          description: 任务 ID
        created:
          description: 请求创建时间，`Unix` 时间戳（秒）
          type: integer
        model:
          description: 模型名称
          type: string
        choices:
          type: array
          description: 模型响应列表
          items:
            type: object
            properties:
              index:
                type: integer
                description: 结果索引
              delta:
                type: object
                description: 模型增量返回的文本信息
                properties:
                  role:
                    type: string
                    description: 当前对话的角色，目前默认为 `assistant`（模型）
                  content:
                    oneOf:
                      - type: string
                        description: >-
                          当前对话文本内容。如果调用函数则为 `null`，否则返回推理结果。

                          对于`GLM-4.5V`系列模型，返回内容可能包含思考过程标签 `<think>
                          </think>`，文本边界标签 `<|begin_of_box|> <|end_of_box|>`。
                      - type: array
                        description: 当前对话的多模态内容（适用于`GLM-4V`系列）
                        items:
                          type: object
                          properties:
                            type:
                              type: string
                              enum:
                                - text
                              description: 内容类型，目前为文本
                            text:
                              type: string
                              description: 文本内容
                      - type: string
                        nullable: true
                        description: 当使用`tool_calls`时，`content`可能为`null`
                  audio:
                    type: object
                    description: 当使用 `glm-4-voice` 模型时返回的音频内容
                    properties:
                      id:
                        type: string
                        description: 当前对话的音频内容`id`，可用于多轮对话输入
                      data:
                        type: string
                        description: 当前对话的音频内容`base64`编码
                      expires_at:
                        type: string
                        description: 当前对话的音频内容过期时间
                  reasoning_content:
                    type: string
                    description: 思维链内容, 仅 `glm-4.5` 系列支持
                  tool_calls:
                    type: array
                    description: 生成的应该被调用的工具信息，流式返回时会逐步生成
                    items:
                      type: object
                      properties:
                        index:
                          type: integer
                          description: 工具调用索引
                        id:
                          type: string
                          description: 工具调用的唯一标识符
                        type:
                          type: string
                          description: 工具类型，目前支持`function`
                          enum:
                            - function
                        function:
                          type: object
                          properties:
                            name:
                              type: string
                              description: 函数名称
                            arguments:
                              type: string
                              description: 函数参数，`JSON`格式字符串
              finish_reason:
                type: string
                description: >-
                  模型推理终止的原因。`stop` 表示自然结束或触发stop词，`tool_calls` 表示模型命中函数，`length`
                  表示达到 `token` 长度限制，`sensitive`
                  表示内容被安全审核接口拦截（用户应判断并决定是否撤回公开内容），`network_error`
                  表示模型推理异常，'model_context_window_exceeded'表示超出模型上下文窗口。
                enum:
                  - stop
                  - length
                  - tool_calls
                  - sensitive
                  - network_error
        usage:
          type: object
          description: 本次模型调用的 `tokens` 数量统计
          properties:
            prompt_tokens:
              type: integer
              description: 用户输入的 `tokens` 数量。对于 `glm-4-voice`，`1`秒音频=`12.5 Tokens`，向上取整。
            completion_tokens:
              type: integer
              description: 模型输出的 `tokens` 数量
            total_tokens:
              type: integer
              description: 总 `tokens` 数量，对于 `glm-4-voice` 模型，`1`秒音频=`12.5 Tokens`，向上取整
        content_filter:
          type: array
          description: 返回内容安全的相关信息
          items:
            type: object
            properties:
              role:
                type: string
                description: >-
                  安全生效环节，包括：`role = assistant` 模型推理，`role = user` 用户输入，`role =
                  history` 历史上下文
              level:
                type: integer
                description: 严重程度 `level 0-3`，`level 0` 表示最严重，`3` 表示轻微
    Error:
      type: object
      properties:
        error:
          required:
            - code
            - message
          type: object
          properties:
            code:
              type: string
            message:
              type: string
    ChatCompletionResponseMessage:
      type: object
      properties:
        role:
          type: string
          description: 当前对话角色，默认为 `assistant`
          example: assistant
        content:
          oneOf:
            - type: string
              description: >-
                当前对话文本内容。如果调用函数则为 `null`，否则返回推理结果。

                对于`GLM-4.5V`系列模型，返回内容可能包含思考过程标签 `<think> </think>`，文本边界标签
                `<|begin_of_box|> <|end_of_box|>`。
            - type: array
              description: 多模态回复内容，适用于`GLM-4V`系列模型
              items:
                type: object
                properties:
                  type:
                    type: string
                    enum:
                      - text
                    description: 回复内容类型，目前为文本
                  text:
                    type: string
                    description: 文本内容
            - type: string
              nullable: true
              description: 当使用`tool_calls`时，`content`可能为`null`
        reasoning_content:
          type: string
          description: 思维链内容，仅在使用 `glm-4.5` 系列, `glm-4.1v-thinking` 系列模型时返回。
        audio:
          type: object
          description: 当使用 `glm-4-voice` 模型时返回的音频内容
          properties:
            id:
              type: string
              description: 当前对话的音频内容`id`，可用于多轮对话输入
            data:
              type: string
              description: 当前对话的音频内容`base64`编码
            expires_at:
              type: string
              description: 当前对话的音频内容过期时间
        tool_calls:
          type: array
          description: 生成的应该被调用的函数名称和参数。
          items:
            $ref: '#/components/schemas/ChatCompletionResponseMessageToolCall'
    ChatCompletionResponseMessageToolCall:
      type: object
      properties:
        function:
          type: object
          description: 包含生成的函数名称和 `JSON` 格式参数。
          properties:
            name:
              type: string
              description: 生成的函数名称。
            arguments:
              type: string
              description: 生成的函数调用参数的 `JSON` 格式字符串。调用函数前请验证参数。
          required:
            - name
            - arguments
        mcp:
          type: object
          description: '`MCP` 工具调用参数'
          properties:
            id:
              description: '`mcp` 工具调用唯一标识'
              type: string
            type:
              description: 工具调用类型, 例如 `mcp_list_tools, mcp_call`
              type: string
              enum:
                - mcp_list_tools
                - mcp_call
            server_label:
              description: '`MCP`服务器标签'
              type: string
            error:
              description: 错误信息
              type: string
            tools:
              description: '`type = mcp_list_tools` 时的工具列表'
              type: array
              items:
                type: object
                properties:
                  name:
                    description: 工具名称
                    type: string
                  description:
                    description: 工具描述
                    type: string
                  annotations:
                    description: 工具注解
                    type: object
                  input_schema:
                    description: 工具输入参数规范
                    type: object
                    properties:
                      type:
                        description: 固定值 'object'
                        type: string
                        default: object
                        enum:
                          - object
                      properties:
                        description: 参数属性定义
                        type: object
                      required:
                        description: 必填属性列表
                        type: array
                        items:
                          type: string
                      additionalProperties:
                        description: 是否允许额外参数
                        type: boolean
            arguments:
              description: 工具调用参数，参数为 `json` 字符串
              type: string
            name:
              description: 工具名称
              type: string
            output:
              description: 工具返回的结果输出
              type: object
        id:
          type: string
          description: 命中函数的唯一标识符。
        type:
          type: string
          description: 调用的工具类型，目前仅支持 'function', 'mcp'。
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      description: >-
        标准的 HTTP Bearer 认证方式，在 [API
        Keys](https://bigmodel.cn/usercenter/proj-mgmt/apikeys) 页面获取密钥。

````
