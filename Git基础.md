# Git 相关

## Stash

需要通过将文件add，加入到暂存区，再进行stash 放入贮藏区

然后unstash pop 将贮藏区的文件应用到当前分支

## Reset的四种模式

### Soft模式

不改变工作目录和暂存区

将被回退的提交放入暂存区

### Mixed模式

清空暂存区

将回退的变更和暂存区中的内容都放入工作目录

### Hard模式

指针前一次提交的所有内容都没了

### Keep模式

这个命令不常用

保留本地的变更，清除被回退的记录

## 开发规范

```
feat: 新增功能（feature）

fix: 修复补丁（bug）

docs: 修订文档，如 Readme, Change Log, Contribute 等

refactor: 代码重构，未新增任何功能和修复任何 bug

style: 仅调整空格、格式缩进等（不改变代码逻辑的变动）

perf: 优化相关，改善性能和体验的修改

test: 测试用例的增加/修改

chore: 非 src 和 test 的修改

merge: 合并分支或冲突等

revert: 回滚到上一个版本

build: 改变构建流程，新增依赖库、工具等（例如 webpack、maven 修改）
```