# BITStudy 教务课表同步设计

日期：2026-06-29

## 目标

参考 `BIT101-1.3.0.apk` 中的课表能力，把 BITStudy 的日程系统升级成“学校课表、ICS、手动日程共用一套规划模型”的结构。

本次设计聚焦第一阶段：

1. 改善首页日程展示，让今天、明天、本周课表更容易扫读
2. 复用现有北理工账号凭据和 CAS 能力，新增教务课表同步边界
3. 把学校课表标准化成现有 `ScheduleEvent`，继续服务首页和 AI 今日计划

## 参考发现

对参考 APK 做静态侦察后，可以看到这些课表相关线索：

- `cn.bit101.android.features.schedule.course`
- `ScheduleItem`
- `ScheduleColorEnum`
- `GetCurrentTermDataModel`
- `GetTermsDataModel`
- `PostGetScheduleDataModel`
- `GetExamsDataModel`
- `SchoolJxzxehallappApiService`
- `SchoolLexueApiService`

参考方向是：学期选择、教务课表拉取、课程表格化展示、自定义日程、加入系统日历。BITStudy 第一阶段只实现教务课表同步和展示升级，不扩展考试、空教室、乐学日历或系统日历写入。

## 用户目标

用户希望：

- 不需要重复输入账号，直接复用 BITStudy 已保存的北理工学号和密码
- 首页能快速看清今天有哪些课、在哪上、什么时候结束
- 本周课表不再只是长列表，而是更接近课程表的按天视图
- 学校同步失败时，不影响已有手动日程和 ICS 日程
- 后续可以继续扩展考试、空教室或乐学日历，而不重写日程系统

## 设计原则

- 统一模型：学校课表、ICS、手动日程最终都转换为 `ScheduleEvent`
- 来源隔离：学校同步只更新学校来源事件，不覆盖手动事件和 ICS 事件
- 渐进接入：先建立教务同步边界和第一版接口实现，后续可替换具体接口细节
- 本地优先：同步后的课表保存为本地副本，离线时仍可查看
- 首页增量升级：继续在 `PlannerSection` 内增强，而不是新增一套复杂导航

## 功能范围

### 本期支持

- 复用 `CredentialManager` 中已加密保存的学号和密码
- 复用或扩展 `BitCasClient` 完成学校系统认证能力
- 拉取当前学期、学期列表和指定学期课表
- 将学校课表映射为 `ScheduleEvent`
- `ScheduleSourceType` 增加 `SCHOOL_IMPORT`
- 同一学期再次同步时，替换该学期的学校来源事件
- 日程区支持 `今天 / 明天 / 本周` 切换
- 本周模式展示按天组织的紧凑课表
- 今天模式强化时间、地点、来源标签
- 同步过程展示加载态、成功态和失败原因

### 本期不做

- 考试安排
- 空教室查询
- 乐学日历同步
- 写入系统日历
- 学校课表与本地修改的冲突预览
- 课表拖拽编辑
- 多账号切换

## 架构设计

新增 `school` 同步层，保持学校接口和首页 UI 解耦。

### SchoolScheduleRepository

职责：

- 读取 `CredentialManager.getCredentials()`
- 没有凭据时返回明确错误
- 调用 `BitCasClient` 或学校认证数据源获取访问能力
- 调用 `SchoolScheduleRemoteDataSource` 拉取学期和课表
- 调用 `SchoolScheduleMapper` 转换为 `ScheduleEvent`
- 将转换后的事件交给 `ScheduleRepository` 按来源和学期应用

### SchoolScheduleRemoteDataSource

职责：

- 封装教务系统请求细节
- 提供当前学期、学期列表、指定学期课表三个接口
- 屏蔽 WebVPN、CAS ticket、cookie 或 service ticket 等底层差异

第一版可以按现有 `BitCasClient` 的模式实现。如果学校接口细节需要继续补样本，则保留清晰的 data source 边界，并让 UI 显示“学校系统响应暂不支持”的错误。

### SchoolScheduleMapper

职责：

- 将学校课表 DTO 映射到 `ScheduleEvent`
- 生成稳定的 `sourceEventUid`
- 计算每节课的日期、开始时间、结束时间和地点
- 保留课程名称、教师、周次、节次等说明信息

映射后的事件字段建议：

- `title`：课程名
- `description`：教师、教学班、周次、节次等
- `location`：教室或线上地址
- `startAt` / `endAt`：具体上课时间
- `sourceType`：`SCHOOL_IMPORT`
- `sourceCalendarId`：`school_<termId>`
- `sourceEventUid`：由学期、课程号、教学班、周次、星期、节次和地点组合生成
- `lastImportedAt`：同步时间

## 数据模型变更

### ScheduleSourceType

新增：

- `SCHOOL_IMPORT`

已有值继续保留：

- `MANUAL`
- `ICS_IMPORT`

### SchoolTerm

新增模型：

- `id`
- `name`
- `startDate`
- `endDate`
- `isCurrent`

### SchoolScheduleSyncResult

新增模型：

- `termId`
- `termName`
- `importedCount`
- `startedAt`
- `finishedAt`
- `message`

## ScheduleRepository 变更

新增按学校来源替换的写入方法：

- `applySchoolSchedule(termId, events)`

规则：

- 删除 `sourceType == SCHOOL_IMPORT` 且 `sourceCalendarId == school_<termId>` 的旧事件
- 写入本次同步得到的新事件
- 不影响 `MANUAL` 和 `ICS_IMPORT`
- 不影响其他学期的学校来源事件

本期不做学校来源事件的本地编辑冲突判断。若用户删除学校来源课程，下次同步会恢复该课程。

## UI 设计

### PlannerHeader

当前导入和添加按钮调整为更明确的操作区：

- 同步教务课表
- 导入 ICS
- 添加日程

如果空间不足，使用一个导入菜单承载：

- `同步教务课表`
- `导入 ICS`
- `手动添加`

### 日程范围切换

在日程页签内增加范围切换：

- `今天`
- `明天`
- `本周`

该切换直接调用已有 `HomeViewModel.setScheduleRange()`，真正启用 `ScheduleRange.TOMORROW` 和 `ScheduleRange.WEEK`。

### 今天视图

展示方式：

- 全天事件在前
- 定时事件按开始时间排序
- 左侧固定时间范围
- 中间显示课程或日程标题
- 下方显示地点
- 右侧或下方显示来源标签：学校、ICS、手动

课程来源事件可使用轻量颜色区分，但不引入一套复杂主题系统。

### 本周视图

展示方式：

- 以未来 7 天为范围
- 每天一个紧凑分组
- 有课程的日期显示课程卡片
- 无课程的日期显示轻量空态
- 课程卡片展示时间、课程名、地点

第一阶段不做横向完整网格，避免在手机屏幕上出现拥挤和文字溢出。

### 同步状态

新增 UI 状态：

- `isSyncingSchoolSchedule`
- `schoolScheduleSyncMessage`
- `schoolScheduleSyncError`
- `availableSchoolTerms`
- `selectedSchoolTerm`

交互规则：

- 点击同步时，如果没有凭据，提示先登录
- 同步中禁用重复点击
- 同步成功后显示导入课程数量
- 同步失败后保留本地已有课表

## ViewModel 流程

新增方法：

- `syncSchoolSchedule()`
- `syncSchoolSchedule(termId: String)`
- `dismissSchoolScheduleSyncMessage()`

默认同步流程：

1. 读取当前学期
2. 拉取当前学期课表
3. 映射为 `ScheduleEvent`
4. 调用 `ScheduleRepository.applySchoolSchedule`
5. 刷新当前范围日程
6. 刷新本地 AI 计划块

## 错误处理

需要区分这些错误：

- 未登录或无保存凭据
- CAS 认证失败
- 学校系统不可达
- 学期列表为空
- 课表为空
- 接口响应格式不支持

错误文案保持可行动：

- 无凭据：`请先登录 BITStudy，再同步教务课表`
- 认证失败：`统一认证失败，请检查学号或密码`
- 网络失败：`暂时无法连接学校系统，请稍后重试`
- 空课表：`这个学期暂时没有可导入课程`
- 格式异常：`学校课表格式暂不支持，已保留本地日程`

## 测试计划

### 单元测试

- `SchoolScheduleMapperTest`
  - 单节课程映射为一条 `ScheduleEvent`
  - 多周课程生成多条稳定 UID 事件
  - 地点、教师、节次写入正确字段

- `ScheduleRepositoryTest`
  - `SCHOOL_IMPORT` 同学期覆盖旧学校事件
  - 覆盖不影响手动事件
  - 覆盖不影响 ICS 事件
  - 覆盖不影响其他学期学校事件

- `ScheduleRangeTest`
  - 今天范围排序
  - 明天范围排序
  - 本周范围按天分组

### 手动验证

- 未登录状态点击同步，看到登录提示
- 已登录状态同步当前学期，首页出现课程
- 再次同步同一学期，课程不重复
- 手动添加日程后同步，手动日程仍保留
- 导入 ICS 后同步，ICS 日程仍保留
- 切换今天、明天、本周，显示稳定且无明显文字溢出

## 实施顺序

1. 扩展模型：`ScheduleSourceType.SCHOOL_IMPORT`、学校学期和同步结果模型
2. 扩展 `ScheduleRepository`：增加学校来源覆盖写入
3. 新增学校同步层：repository、remote data source、mapper
4. 接入 `HomeViewModel`：同步状态、同步方法、范围切换状态
5. 优化 `PlannerSection`：操作入口、范围切换、今天视图、本周视图
6. 补充单元测试
7. 手动运行应用验证同步失败和本地显示路径

## 风险与约束

- 学校教务接口可能变更，第一版必须把接口细节隔离在 remote data source 内
- 复用学号密码需要保持加密存储，不在日志或错误信息中输出凭据
- 若学校系统必须通过 WebVPN，优先复用 `BitCasClient.convertToWebVpnUrl`
- 课表节次到具体时间需要学校作息表；第一版可以内置北理工常用作息映射，并集中放在 mapper 或配置对象中
- 手机屏幕无法承载复杂周网格，第一版使用按天紧凑视图

## 验收标准

- 日程区能切换今天、明天、本周
- 本周视图比普通长列表更容易扫读课程
- 点击同步时会复用现有保存凭据
- 同步成功后学校课表进入现有 `ScheduleEvent` 数据流
- 同步失败不会清空或破坏本地日程
- 再次同步同一学期不会产生重复课程
- AI 今日计划仍可读取同步后的课程安排
