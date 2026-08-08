package com.jokerhub.paper.plugin.orzmc.features.review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 审核记录持久化端口。
 *
 * <p>框架侧只依赖本接口，具体存储实现（YAML / DB）由宿主注入，
 * 保证 review 包可整体拆出为独立插件。</p>
 */
public interface ReviewStore {

    /** 保存（新增或覆盖）一条审核记录。 */
    void save(ReviewRequest request);

    /** 按 id 查询。 */
    Optional<ReviewRequest> findById(String id);

    /** 全部待审记录（按时间序，先提交在前）。 */
    List<ReviewRequest> listPending();

    /** 某玩家的全部申请记录（含历史，/apply status 用）。 */
    List<ReviewRequest> listByApplicant(UUID applicantId);

    /** 玩家在某类型下的待审申请（唯一）。 */
    Optional<ReviewRequest> pendingFor(String typeId, UUID applicantId);

    /** 玩家在某类型下是否已有待审申请（防重复提交）。 */
    boolean hasPending(String typeId, UUID applicantId);
}
