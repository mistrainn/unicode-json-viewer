# Unicode JSON Viewer (Burp Montoya Extension)

## 功能
- 在 Burp 请求/响应视图中新增 `Decoded JSON` 标签页（只读，不改原始报文）。
- 标签页完整保留请求/响应（起始行 + 头部 + body），并对 body 做解码与格式化。
- 自动解码中文 `\uXXXX`（如 `\u4f60\u597d -> 你好`）。
- 自动格式化 JSON。
- 递归展开嵌套 JSON 字符串，例如：
  - 输入：`{"123":"{\"321\":\"\\u4f60\\u597d\"}"}`
  - 显示：`123` 字段会展开为对象，值为 `{"321":"你好"}`（并美化缩进）。
- 新标签页自带 JSON 语法高亮（key/string/number/boolean/null

<img width="562" height="190" alt="image" src="https://github.com/user-attachments/assets/1a05f563-b823-4030-89a2-8f10e5e6cb83" />
<img width="559" height="204" alt="image" src="https://github.com/user-attachments/assets/fe866e10-192d-4e75-819c-8fb1ed2acc4e" />


<img width="1142" height="607" alt="image" src="https://github.com/user-attachments/assets/18874022-a71e-4ce6-b041-09cd624abd4f" />
<img width="1134" height="619" alt="image" src="https://github.com/user-attachments/assets/3ab9ec35-bc05-4ca3-b3ba-4b217890295f" />



## 构建
```bash
mvn clean package
```

打包后 Jar 位于：
- `target/unicode-json-viewer-1.0.0.jar`

## 在 Burp 中加载
1. 打开 Burp Suite。
2. 进入 `Extensions` -> `Installed`。
3. 点击 `Add`，选择上面的 Jar。
4. 类型选 `Java`。

加载成功后，在请求/响应编辑器会看到 `Decoded JSON` 标签页。
