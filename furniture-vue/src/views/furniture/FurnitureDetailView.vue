<template>
  <div class="furniture-container">
    <!-- Breadcrumb -->
    <div class="detail-breadcrumb">
      <button class="breadcrumb-back" @click="goBack" title="返回">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
      </button>
      <router-link to="/">首页</router-link>
      <span>/</span>
      <span
        v-if="typeInfo.id"
        class="breadcrumb-link"
        @click="goToType(typeInfo.id)"
        >{{ typeInfo.name || "家具分类" }}</span
      >
      <span v-else>/</span>
      <span v-if="typeInfo.id">/</span>
      <span class="current">{{ furniture.fName || "家具详情" }}</span>
    </div>

    <!-- 主体内容 -->
    <main class="main-content" v-if="loading">
      <div class="loading-state">
        <div class="spinner"></div>
        <p>正在加载家具信息...</p>
      </div>
    </main>

    <main class="main-content" v-else-if="!furniture.id">
      <div class="empty-state">
        <div class="empty-icon">📦</div>
        <p>家具不存在或已下架</p>
        <button class="back-btn-large" @click="goBack">返回列表</button>
      </div>
    </main>

    <main class="main-content" v-else>
      <div class="detail-layout">
        <!-- 左侧：图片区域 -->
        <div class="image-section">
          <div class="main-image">
            <div class="image-placeholder-large">
              <img
                loading="lazy"
                v-if="!mainImgError"
                :src="imgUrl(currentImage)"
                :alt="furniture.fName"
                class="furniture-img-real"
                @error="handleImgError"
                @click="previewImage(currentImage)"
              />
              <span v-else class="img-fallback">🪑</span>
            </div>
            <div class="stock-tag" :class="{ 'low-stock': displayStock < 10 }">
              库存 {{ displayStock }}
            </div>
          </div>
          <div class="thumbnail-list" v-if="allImages.length > 1">
            <img
              loading="lazy"
              v-for="(img, idx) in allImages"
              :key="idx"
              :src="imgUrl(img)"
              class="thumbnail"
              :class="{ active: currentImage === img }"
              @click="currentImage = img"
              @error="handleThumbError"
            />
          </div>
        </div>

        <!-- 右侧：信息区域 -->
        <div class="info-section">
          <div class="info-header">
            <h1 class="furniture-name">{{ furniture.fName }}</h1>
            <p class="furniture-brand" v-if="furniture.brand">
              <span></span> {{ furniture.brand }}
            </p>
          </div>

          <div class="price-section">
            <span class="price-label">售价</span>
            <span class="price-value">¥{{ formatPrice(displayPrice) }}</span>
          </div>

          <!-- 规格选择器 -->
          <div class="spec-section" v-if="hasSpecs">
            <div class="spec-group" v-for="group in specGroups" :key="group.id">
              <div class="spec-group-label">{{ group.groupName }}</div>
              <div class="spec-values">
                <div
                  v-for="val in group.values"
                  :key="val.id"
                  class="spec-value-item"
                  :class="{
                    active: selectedSpecs[group.groupName] === val.valueName,
                    disabled: !isSpecValueAvailable(
                      group.groupName,
                      val.valueName,
                    ),
                  }"
                  @click="selectSpec(group.groupName, val.valueName)"
                >
                  <img
                    loading="lazy"
                    v-if="val.valueImage"
                    :src="imgUrl(val.valueImage)"
                    class="spec-value-img"
                  />
                  <span>{{ val.valueName }}</span>
                </div>
              </div>
            </div>
            <div class="spec-selected-info" v-if="selectedSku">
              <span class="selected-label">已选：</span>
              <span class="selected-text">{{ selectedSku.specText }}</span>
            </div>
          </div>

          <div class="intro-section" v-if="furniture.intro">
            <h3>产品介绍</h3>
            <p class="intro-text">{{ furniture.intro }}</p>
          </div>

          <div class="action-section">
            <div class="quantity-selector">
              <span class="label">数量</span>
              <div class="quantity-control">
                <button
                  class="qty-btn"
                  @click="decreaseQty"
                  :disabled="quantity <= 1"
                >
                  -
                </button>
                <span class="qty-value">{{ quantity }}</span>
                <button
                  class="qty-btn"
                  @click="increaseQty"
                  :disabled="quantity >= displayStock"
                >
                  +
                </button>
              </div>
              <span class="stock-hint" v-if="selectedSku"
                >库存 {{ displayStock }} 件</span
              >
            </div>
            <div class="action-buttons">
              <button class="btn-cart" @click="addToCart">
                <span></span> 加入购物车
              </button>
              <button
                class="btn-buy"
                @click="buyNow"
                :disabled="displayStock <= 0"
              >
                <span></span> 立即购买
              </button>
              <button
                class="btn-fav"
                :class="{ favorited: isFavorited }"
                @click="handleToggleFav"
              >
                <span>{{ isFavorited ? "❤️" : "🤍" }}</span>
                {{ isFavorited ? "已收藏" : "收藏" }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab 信息区 -->
      <div class="detail-tabs" v-if="furniture.id">
        <nav class="tabs-nav">
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'detail' }"
            @click="activeTab = 'detail'"
          >
            商品详情
          </button>
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'specs' }"
            @click="activeTab = 'specs'"
          >
            规格参数
          </button>
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'reviews' }"
            @click="activeTab = 'reviews'"
          >
            商品评价
            <span class="tab-badge" v-if="reviewStats.reviewCount > 0">{{
              reviewStats.reviewCount
            }}</span>
          </button>
        </nav>

        <div class="tab-content" v-show="activeTab === 'detail'">
          <div class="detail-content">
            <p v-if="furniture.intro">{{ furniture.intro }}</p>
            <div class="detail-placeholder" v-if="!furniture.intro">
              <p>该商品暂无详细介绍，如需了解更多信息请联系客服。</p>
            </div>
            <!-- 多图展示 -->
            <div class="detail-images" v-if="allImages.length > 0">
              <img
                loading="lazy"
                v-for="(img, idx) in allImages"
                :key="'det' + idx"
                :src="imgUrl(img)"
                class="detail-img"
                @click="previewImage(img)"
                @error="(e) => (e.target.style.display = 'none')"
              />
            </div>
          </div>
        </div>

        <div class="tab-content" v-show="activeTab === 'specs'">
          <div class="specs-table" v-if="hasSpecs">
            <div class="specs-row" v-for="group in specGroups" :key="group.id">
              <span class="specs-label">{{ group.groupName }}</span>
              <span class="specs-values">{{
                group.values.map((v) => v.valueName).join(" / ")
              }}</span>
            </div>
          </div>
          <div class="specs-table" v-if="furniture.brand">
            <div class="specs-row">
              <span class="specs-label">品牌</span>
              <span class="specs-values">{{ furniture.brand }}</span>
            </div>
          </div>
          <div class="specs-table" v-if="furniture.material">
            <div class="specs-row">
              <span class="specs-label">材质</span>
              <span class="specs-values">{{ furniture.material }}</span>
            </div>
          </div>
          <div
            class="detail-placeholder"
            v-if="!hasSpecs && !furniture.brand && !furniture.material"
          >
            <p>暂无规格参数信息</p>
          </div>
        </div>

        <div class="tab-content" v-show="activeTab === 'reviews'">
          <!-- 评价区域 -->
          <div class="review-section">
            <div class="review-head">
              <h3>商品评价</h3>
              <div class="review-scorecard" v-if="reviewStats.reviewCount > 0">
                <span class="score-big">{{ reviewStats.avgRating }}</span>
                <div class="score-meta">
                  <span class="score-stars">{{
                    "⭐".repeat(reviewRatingStars)
                  }}</span>
                  <span class="score-count"
                    >共 {{ reviewStats.reviewCount }} 条评价</span
                  >
                </div>
              </div>
            </div>

            <div class="review-body">
              <div class="review-list" v-if="reviewList.length > 0">
                <div
                  class="review-card"
                  :id="'review-' + r.id"
                  v-for="r in reviewList.slice(0, 2)"
                  :key="r.id"
                >
                  <div
                    v-if="r.userDeleted === 1 || r.deleted === 1"
                    class="review-deleted-placeholder"
                  >
                    <span>该评价已删除</span>
                  </div>
                  <div v-else>
                    <div class="review-card-hd">
                      <img
                        loading="lazy"
                        v-if="r.userAvatar"
                        :src="imgUrl(r.userAvatar)"
                        class="review-avatar"
                        @error="(e) => (e.target.style.display = 'none')"
                      />
                      <span v-else class="review-avatar-placeholder">👤</span>
                      <span class="review-user">{{
                        r.isAnonym && r.userId !== currentUserId
                          ? "匿名用户"
                          : r.userName || "用户"
                      }}</span>
                      <span class="review-stars">{{
                        "⭐".repeat(r.score)
                      }}</span>
                      <el-tag v-if="r.status === 0" type="warning" size="small"
                        >审核中</el-tag
                      >
                      <el-tag v-if="r.status === 2" type="danger" size="small"
                        >审核未通过</el-tag
                      >
                      <span class="review-time">{{
                        formatTimeFull(r.createTime)
                      }}</span>
                      <el-button
                        v-if="r.userId === currentUserId"
                        text
                        type="danger"
                        size="small"
                        @click="handleDeleteReview(r.id)"
                        style="margin-left: 8px"
                        >删除
                      </el-button>
                    </div>
                    <p class="review-text" v-if="r.content">{{ r.content }}</p>
                    <div class="review-media" v-if="r.imgUrl || r.videoUrl">
                      <img
                        loading="lazy"
                        v-for="(img, idx) in parseImages(r.imgUrl)"
                        :key="idx"
                        :src="imgUrl(img)"
                        class="review-img"
                        @click="previewImage(img)"
                        @error="(e) => (e.target.style.display = 'none')"
                      />
                      <video
                        v-if="r.videoUrl"
                        :src="imgUrl(r.videoUrl)"
                        controls
                        class="review-video"
                        @click="previewVideo(imgUrl(r.videoUrl))"
                      ></video>
                    </div>
                    <!-- 追评 -->
                    <div
                      class="review-append"
                      v-for="a in r.appendList"
                      :key="a.id"
                    >
                      <div
                        v-if="a.userDeleted === 1 || a.deleted === 1"
                        class="review-deleted-placeholder"
                      >
                        <span>该追评已删除</span>
                      </div>
                      <template v-else>
                        <div class="append-hd">
                          <span class="append-tag"
                            >追评{{
                              a.appendNum === 1 ? "" : a.appendNum
                            }}</span
                          >
                          <el-tag
                            v-if="a.status === 0"
                            type="warning"
                            size="small"
                            >审核中</el-tag
                          >
                          <el-tag
                            v-if="a.status === 2"
                            type="danger"
                            size="small"
                            >审核未通过</el-tag
                          >
                          <el-button
                            v-if="a.userId === currentUserId"
                            text
                            type="danger"
                            size="small"
                            @click="handleDeleteAppend(a.id, r.id)"
                            style="margin-left: auto"
                            >删除
                          </el-button>
                        </div>
                        <p class="append-text">{{ a.appendContent }}</p>
                        <div class="review-media" v-if="a.appendImg">
                          <img
                            loading="lazy"
                            v-for="(img, idx) in parseAppendImages(a.appendImg)"
                            :key="'a' + idx"
                            :src="imgUrl(img)"
                            class="review-img"
                            @click="previewImage(img)"
                            @error="(e) => (e.target.style.display = 'none')"
                          />
                        </div>
                        <span class="append-time">{{
                          formatTimeFull(a.appendTime)
                        }}</span>
                      </template>
                    </div>

                    <!-- 评论区 -->
                    <div class="review-comment-section">
                      <div class="comment-toggle" @click="toggleComments(r.id)">
                        <el-icon>
                          <ChatLineSquare />
                        </el-icon>
                        <span
                          >评论 ({{ reviewCommentCountMap[r.id] || 0 }})</span
                        >
                        <el-icon
                          class="toggle-arrow"
                          :class="{ 'is-expanded': expandedReviews[r.id] }"
                        >
                          <ArrowDown />
                        </el-icon>
                      </div>
                      <div v-if="expandedReviews[r.id]" class="comment-panel">
                        <!-- 评论列表 -->
                        <div
                          class="comment-list"
                          v-if="reviewCommentsMap[r.id]?.length > 0"
                        >
                          <div
                            v-for="c in reviewCommentsMap[r.id]"
                            :key="c.id"
                            class="comment-item"
                          >
                            <div
                              v-if="c.userDeleted === 1 || c.deleted === 1"
                              class="review-deleted-placeholder"
                            >
                              <span>该评论已删除</span>
                            </div>
                            <template v-else>
                              <img
                                loading="lazy"
                                v-if="c.userAvatar"
                                :src="imgUrl(c.userAvatar)"
                                class="comment-avatar"
                                @error="
                                  (e) => (e.target.style.display = 'none')
                                "
                              />
                              <span v-else class="comment-avatar-placeholder"
                                >👤</span
                              >
                              <div class="comment-body">
                                <div class="comment-hd">
                                  <span class="comment-user">{{
                                    c.userName || "用户"
                                  }}</span>
                                  <span
                                    v-if="c.replyToUserName"
                                    class="comment-reply-to"
                                  >
                                    回复
                                    <span class="reply-user">{{
                                      c.replyToUserName
                                    }}</span>
                                  </span>
                                  <span class="comment-time">{{
                                    formatTimeFull(c.createTime)
                                  }}</span>
                                </div>
                                <p class="comment-content">{{ c.content }}</p>
                                <div class="comment-actions">
                                  <el-button
                                    text
                                    size="small"
                                    @click="replyToComment(r, c)"
                                    >回复</el-button
                                  >
                                  <el-button
                                    v-if="c.userId === currentUserId"
                                    text
                                    size="small"
                                    type="danger"
                                    @click="
                                      handleDeleteReviewComment(c.id, r.id)
                                    "
                                    >删除
                                  </el-button>
                                </div>
                                <!-- 子回复 -->
                                <div
                                  v-if="c.children?.length > 0"
                                  class="comment-children"
                                >
                                  <div
                                    v-for="child in c.children"
                                    :key="child.id"
                                    class="comment-child-item"
                                  >
                                    <div
                                      v-if="
                                        child.userDeleted === 1 ||
                                        child.deleted === 1
                                      "
                                      class="review-deleted-placeholder"
                                    >
                                      <span>该评论已删除</span>
                                    </div>
                                    <template v-else>
                                      <img
                                        loading="lazy"
                                        v-if="child.userAvatar"
                                        :src="imgUrl(child.userAvatar)"
                                        class="comment-avatar-small"
                                        @error="
                                          (e) =>
                                            (e.target.style.display = 'none')
                                        "
                                      />
                                      <span
                                        v-else
                                        class="comment-avatar-placeholder-small"
                                        >👤</span
                                      >
                                      <div class="comment-child-body">
                                        <div class="comment-hd">
                                          <span class="comment-user">{{
                                            child.userName || "用户"
                                          }}</span>
                                          <span
                                            v-if="child.replyToUserName"
                                            class="comment-reply-to"
                                          >
                                            回复
                                            <span class="reply-user">{{
                                              child.replyToUserName
                                            }}</span>
                                          </span>
                                          <span class="comment-time">{{
                                            formatTimeFull(child.createTime)
                                          }}</span>
                                        </div>
                                        <p class="comment-content">
                                          {{ child.content }}
                                        </p>
                                        <div class="comment-actions">
                                          <el-button
                                            text
                                            size="small"
                                            @click="replyToComment(r, child)"
                                            >回复</el-button
                                          >
                                          <el-button
                                            v-if="
                                              child.userId === currentUserId
                                            "
                                            text
                                            size="small"
                                            type="danger"
                                            @click="
                                              handleDeleteReviewComment(
                                                child.id,
                                                r.id,
                                              )
                                            "
                                            >删除
                                          </el-button>
                                        </div>
                                      </div>
                                    </template>
                                  </div>
                                </div>
                              </div>
                            </template>
                          </div>
                        </div>
                        <!-- 评论输入框 -->
                        <div class="comment-input-wrapper">
                          <el-input
                            v-model="commentInputMap[r.id]"
                            :placeholder="
                              commentPlaceholderMap[r.id] || '写评论...'
                            "
                            size="small"
                            @keyup.enter="submitReviewComment(r.id, r.userId)"
                          >
                            <template #append>
                              <el-button
                                @click="submitReviewComment(r.id, r.userId)"
                                :loading="commentSubmittingMap[r.id]"
                              >
                                发送
                              </el-button>
                            </template>
                          </el-input>
                          <el-button
                            v-if="commentReplyToMap[r.id]"
                            text
                            size="small"
                            @click="cancelReply(r.id)"
                          >
                            取消回复
                          </el-button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="review-empty" v-else>
                <p>暂无评价，成为第一个评价的人吧</p>
              </div>
              <div class="review-more" v-if="reviewList.length > 2">
                <el-button text type="primary" @click="showAllReviews"
                  >查看全部 {{ reviewList.length }} 条评价</el-button
                >
              </div>
            </div>
          </div>
          <!-- end review-section -->
        </div>
        <!-- end tab-content reviews -->
      </div>
      <!-- end detail-tabs -->

      <!-- 相关推荐 -->
      <div class="related-section" v-if="relatedProducts.length > 0">
        <h3 class="related-title">相关推荐</h3>
        <div class="related-grid">
          <ProductCard v-for="p in relatedProducts" :key="p.id" :product="p" />
        </div>
      </div>
    </main>

    <!-- 移动端底部固定购买栏 -->
    <div class="sticky-bar" v-if="furniture.id && !loading">
      <div class="sticky-bar-inner">
        <div class="sticky-info">
          <span class="sticky-price">¥{{ formatPrice(displayPrice) }}</span>
          <span class="sticky-stock" v-if="displayStock > 0">有货</span>
        </div>
        <div class="sticky-actions">
          <button class="sticky-btn cart" @click="addToCart">加入购物车</button>
          <button
            class="sticky-btn buy"
            @click="buyNow"
            :disabled="displayStock <= 0"
          >
            立即购买
          </button>
        </div>
      </div>
    </div>

    <!-- 全部评价弹窗 -->
    <el-dialog
      v-model="reviewDialogVisible"
      title="全部评价"
      width="650px"
      :close-on-click-modal="true"
    >
      <div class="review-dialog-content">
        <div class="review-dialog-stats" v-if="reviewStats.reviewCount > 0">
          <span class="score-big">{{ reviewStats.avgRating }}</span>
          <div class="score-meta">
            <span class="score-stars">{{
              "⭐".repeat(reviewRatingStars)
            }}</span>
            <span class="score-count">共 {{ reviewList.length }} 条评价</span>
          </div>
        </div>
        <el-divider />
        <div class="review-dialog-list">
          <div
            class="review-card"
            :id="'review-dialog-' + r.id"
            v-for="r in reviewList"
            :key="r.id"
          >
            <div
              v-if="r.userDeleted === 1 || r.deleted === 1"
              class="review-deleted-placeholder"
            >
              <span>该评价已删除</span>
            </div>
            <div v-else>
              <div class="review-card-hd">
                <img
                  loading="lazy"
                  v-if="r.userAvatar"
                  :src="imgUrl(r.userAvatar)"
                  class="review-avatar"
                  @error="(e) => (e.target.style.display = 'none')"
                />
                <span v-else class="review-avatar-placeholder">👤</span>
                <span class="review-user">{{
                  r.isAnonym && r.userId !== currentUserId
                    ? "匿名用户"
                    : r.userName || "用户"
                }}</span>
                <span class="review-stars">{{ "⭐".repeat(r.score) }}</span>
                <el-tag v-if="r.status === 0" type="warning" size="small"
                  >审核中</el-tag
                >
                <el-tag v-if="r.status === 2" type="danger" size="small"
                  >审核未通过</el-tag
                >
                <span class="review-time">{{
                  formatTimeFull(r.createTime)
                }}</span>
                <el-button
                  v-if="r.userId === currentUserId"
                  text
                  type="danger"
                  size="small"
                  @click="handleDeleteReview(r.id)"
                  style="margin-left: 8px"
                  >删除
                </el-button>
              </div>
              <p class="review-text" v-if="r.content">{{ r.content }}</p>
              <div class="review-media" v-if="r.imgUrl || r.videoUrl">
                <img
                  loading="lazy"
                  v-for="(img, idx) in parseImages(r.imgUrl)"
                  :key="idx"
                  :src="imgUrl(img)"
                  class="review-img"
                  @click="previewImage(img)"
                  @error="(e) => (e.target.style.display = 'none')"
                />
                <video
                  v-if="r.videoUrl"
                  :src="imgUrl(r.videoUrl)"
                  controls
                  class="review-video"
                  @click="previewVideo(imgUrl(r.videoUrl))"
                ></video>
              </div>
              <!-- 追评 -->
              <div class="review-append" v-for="a in r.appendList" :key="a.id">
                <div
                  v-if="a.userDeleted === 1 || a.deleted === 1"
                  class="review-deleted-placeholder"
                >
                  <span>该追评已删除</span>
                </div>
                <template v-else>
                  <div class="append-tag">
                    追评{{ a.appendNum === 1 ? "" : a.appendNum }}
                  </div>
                  <p class="append-text">{{ a.appendContent }}</p>
                  <div class="review-media" v-if="a.appendImg">
                    <img
                      loading="lazy"
                      v-for="(img, idx) in parseAppendImages(a.appendImg)"
                      :key="'a' + idx"
                      :src="imgUrl(img)"
                      class="review-img"
                      @click="previewImage(img)"
                      @error="(e) => (e.target.style.display = 'none')"
                    />
                  </div>
                  <span class="append-time">{{
                    formatTimeFull(a.appendTime)
                  }}</span>
                  <el-button
                    v-if="a.userId === currentUserId"
                    text
                    type="danger"
                    size="small"
                    @click="handleDeleteAppend(a.id, r.id)"
                    style="margin-left: 8px"
                    >删除
                  </el-button>
                </template>
              </div>

              <!-- 评论区 -->
              <div class="review-comment-section">
                <div class="comment-toggle" @click="toggleComments(r.id)">
                  <el-icon>
                    <ChatLineSquare />
                  </el-icon>
                  <span>评论 ({{ reviewCommentCountMap[r.id] || 0 }})</span>
                  <el-icon
                    class="toggle-arrow"
                    :class="{ 'is-expanded': expandedReviews[r.id] }"
                  >
                    <ArrowDown />
                  </el-icon>
                </div>
                <div v-if="expandedReviews[r.id]" class="comment-panel">
                  <!-- 评论列表 -->
                  <div
                    class="comment-list"
                    v-if="reviewCommentsMap[r.id]?.length > 0"
                  >
                    <div
                      v-for="c in reviewCommentsMap[r.id]"
                      :key="c.id"
                      :id="'review-comment-' + c.id"
                      class="comment-item"
                    >
                      <div
                        v-if="c.userDeleted === 1 || c.deleted === 1"
                        class="review-deleted-placeholder"
                      >
                        <span>该评论已删除</span>
                      </div>
                      <template v-else>
                        <img
                          loading="lazy"
                          v-if="c.userAvatar"
                          :src="imgUrl(c.userAvatar)"
                          class="comment-avatar"
                          @error="(e) => (e.target.style.display = 'none')"
                        />
                        <span v-else class="comment-avatar-placeholder"
                          >👤</span
                        >
                        <div class="comment-body">
                          <div class="comment-hd">
                            <span class="comment-user">{{
                              c.userName || "用户"
                            }}</span>
                            <span
                              v-if="c.replyToUserName"
                              class="comment-reply-to"
                            >
                              回复
                              <span class="reply-user">{{
                                c.replyToUserName
                              }}</span>
                            </span>
                            <span class="comment-time">{{
                              formatTimeFull(c.createTime)
                            }}</span>
                          </div>
                          <p class="comment-content">{{ c.content }}</p>
                          <div class="comment-actions">
                            <el-button
                              text
                              size="small"
                              @click="replyToComment(r, c)"
                              >回复</el-button
                            >
                            <el-button
                              v-if="c.userId === currentUserId"
                              text
                              size="small"
                              type="danger"
                              @click="handleDeleteReviewComment(c.id, r.id)"
                              >删除
                            </el-button>
                          </div>
                          <!-- 子回复 -->
                          <div
                            v-if="c.children?.length > 0"
                            class="comment-children"
                          >
                            <div
                              v-for="child in c.children"
                              :key="child.id"
                              :id="'review-comment-' + child.id"
                              class="comment-child-item"
                            >
                              <div
                                v-if="
                                  child.userDeleted === 1 || child.deleted === 1
                                "
                                class="review-deleted-placeholder"
                              >
                                <span>该评论已删除</span>
                              </div>
                              <template v-else>
                                <img
                                  loading="lazy"
                                  v-if="child.userAvatar"
                                  :src="imgUrl(child.userAvatar)"
                                  class="comment-avatar-small"
                                  @error="
                                    (e) => (e.target.style.display = 'none')
                                  "
                                />
                                <span
                                  v-else
                                  class="comment-avatar-placeholder-small"
                                  >👤</span
                                >
                                <div class="comment-child-body">
                                  <div class="comment-hd">
                                    <span class="comment-user">{{
                                      child.userName || "用户"
                                    }}</span>
                                    <span
                                      v-if="child.replyToUserName"
                                      class="comment-reply-to"
                                    >
                                      回复
                                      <span class="reply-user">{{
                                        child.replyToUserName
                                      }}</span>
                                    </span>
                                    <span class="comment-time">{{
                                      formatTimeFull(child.createTime)
                                    }}</span>
                                  </div>
                                  <p class="comment-content">
                                    {{ child.content }}
                                  </p>
                                  <div class="comment-actions">
                                    <el-button
                                      text
                                      size="small"
                                      @click="replyToComment(r, child)"
                                      >回复</el-button
                                    >
                                    <el-button
                                      v-if="child.userId === currentUserId"
                                      text
                                      size="small"
                                      type="danger"
                                      @click="
                                        handleDeleteReviewComment(
                                          child.id,
                                          r.id,
                                        )
                                      "
                                      >删除
                                    </el-button>
                                  </div>
                                </div>
                              </template>
                            </div>
                          </div>
                        </div>
                      </template>
                    </div>
                  </div>
                  <!-- 评论输入框 -->
                  <div class="comment-input-wrapper">
                    <el-input
                      v-model="commentInputMap[r.id]"
                      :placeholder="commentPlaceholderMap[r.id] || '写评论...'"
                      size="small"
                      @keyup.enter="submitReviewComment(r.id, r.userId)"
                    >
                      <template #append>
                        <el-button
                          @click="submitReviewComment(r.id, r.userId)"
                          :loading="commentSubmittingMap[r.id]"
                        >
                          发送
                        </el-button>
                      </template>
                    </el-input>
                    <el-button
                      v-if="commentReplyToMap[r.id]"
                      text
                      size="small"
                      @click="cancelReply(r.id)"
                    >
                      取消回复
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 图片预览弹窗 -->
    <el-dialog
      v-model="imagePreviewVisible"
      title="图片预览"
      width="700px"
      :close-on-click-modal="true"
      class="image-preview-dialog"
    >
      <div class="image-preview-body">
        <img
          loading="lazy"
          :src="previewImageUrl"
          class="preview-img-full"
          @error="(e) => (e.target.style.display = 'none')"
        />
      </div>
    </el-dialog>

    <!-- 视频预览弹窗 -->
    <el-dialog
      v-model="videoPreviewVisible"
      title="视频预览"
      width="700px"
      :close-on-click-modal="true"
      class="video-preview-dialog"
    >
      <div class="video-preview-body">
        <video
          :src="previewVideoUrl"
          controls
          class="preview-video-full"
        ></video>
      </div>
    </el-dialog>

    <!-- 购买对话框 -->
    <el-dialog
      v-model="buyDialogVisible"
      title="确认订单信息"
      width="500px"
      :close-on-click-modal="false"
      class="buy-dialog"
    >
      <div class="order-summary">
        <div class="summary-item">
          <img
            loading="lazy"
            :src="imgUrl(displayImage || furniture.fIcon)"
            class="summary-img"
            @error="handleSummaryImgError"
          />
          <div class="summary-info">
            <p class="summary-name">{{ furniture.fName }}</p>
            <p class="summary-spec" v-if="selectedSku">
              {{ selectedSku.specText }}
            </p>
            <p class="summary-price">
              ¥{{ formatPrice(displayPrice) }} × {{ quantity }}
            </p>
          </div>
          <div class="summary-total">
            ¥{{ formatPrice(displayPrice * quantity) }}
          </div>
        </div>
      </div>

      <!-- 优惠券 -->
      <div class="buy-coupon">
        <div class="coupon-select-hd">
          <span class="label">优惠券</span>
          <button class="picker" @click="showBuyCouponDialog = true">
            <span v-if="selectedBuyCouponIds.length">
              {{ buyCouponText }}
              <em v-if="buyDiscountEstimate > 0" class="picker-save"
                >-{{ formatPrice(buyDiscountEstimate) }}</em
              >
            </span>
            <span v-else>{{ buyAvailableCount ? "选择优惠券" : "暂无可用优惠券" }}</span>
            <span class="arrow">›</span>
          </button>
        </div>

        <CouponPickerDialog
          v-model="showBuyCouponDialog"
          v-model:selected-ids="selectedBuyCouponIds"
          :coupons="buyCoupons"
          :items="buyCheckoutItems"
        />
      </div>

      <!-- 应付合计 -->
      <div class="buy-total">
        <div v-if="buyDiscountEstimate > 0" class="buy-og">
          商品总额 ¥{{ formatPrice(displayBuyTotal) }}，优惠
          -{{ formatPrice(buyDiscountEstimate) }}
        </div>
        <div class="buy-pay">
          应付金额：<b>¥{{ formatPrice(displayBuyTotal - buyDiscountEstimate) }}</b>
        </div>
      </div>

      <el-form :model="buyForm" label-position="top" class="buy-form">
        <el-form-item label="收货地址">
          <el-select
            v-model="selectedAddressId"
            placeholder="请选择收货地址"
            @change="onAddressSelect"
            style="width: 100%"
          >
            <el-option
              v-for="addr in savedAddresses"
              :key="addr.id"
              :label="addr.consignee + ' ' + addr.phone + ' ' + addr.address"
              :value="addr.id"
            >
              <span>{{ addr.consignee }} — {{ addr.phone }}</span>
              <span style="color: var(--color-text-tertiary); font-size: 12px; display: block">{{
                addr.address
              }}</span>
            </el-option>
            <el-option :value="0" label="使用新地址">
              <span style="color: var(--color-accent)">+ 使用新地址</span>
            </el-option>
          </el-select>
          <div v-if="savedAddresses.length === 0" class="form-tip">
            <el-text type="info" size="small"
              >暂无已保存地址，请先
              <el-link type="primary" @click="goToAddresses">添加地址</el-link>
            </el-text>
          </div>
        </el-form-item>

        <template v-if="useNewAddress">
          <el-form-item label="收货人姓名 *">
            <el-input
              v-model="buyForm.consignee"
              placeholder="请输入收货人姓名"
              maxlength="20"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="联系电话 *">
            <el-input
              v-model="buyForm.phone"
              placeholder="请输入联系电话"
              maxlength="20"
            />
          </el-form-item>

          <el-form-item label="详细地址 *">
            <el-input
              v-model="buyForm.address"
              type="textarea"
              :rows="3"
              placeholder="省/市/区 + 街道门牌号"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>

          <el-form-item>
            <el-checkbox v-model="saveAddressToBook">
              保存到我的地址簿，下次下单可直接选用
            </el-checkbox>
          </el-form-item>
        </template>

        <el-form-item label="订单备注">
          <el-input
            v-model="buyForm.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入订单备注（选填）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeBuyDialog" size="large">取消</el-button>
          <el-button
            type="primary"
            @click="handleSubmitBuy"
            :loading="buyLoading"
            class="submit-btn"
            size="large"
          >
            提交订单
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowDown, ChatLineSquare } from "@element-plus/icons-vue";
import { useFurnitureDetail } from "@/composables/useFurniture.js";
import { useBackNavigation } from '@/composables/useBackNavigation.js';
import { useRequireLogin } from '@/composables/useRequireLogin.js';
import { imgUrl } from "@/utils/img.js";
import { formatTimeFull } from "@/utils/format.js";
import { logger } from "@/utils/logger.js";

import { useCartStore } from "@/stores/cart.js";
import { checkFavorite, toggleFavorite } from "@/api/favorite.js";
import { getAddressList, saveAddress } from "@/api/address.js";
import { deleteAppend, deleteReview, getComments } from "@/api/comment.js";
import { getMyCoupons } from "@/api/coupon.js";
import {
  addReviewComment,
  deleteReviewComment,
  getReviewComments,
} from "@/api/reviewComment.js";
import { getFurnitureByTypeId } from "@/api/furniture.js";
import ProductCard from "@/components/product/ProductCard.vue";
import CouponPickerDialog from "@/components/coupon/CouponPickerDialog.vue";
import { useCouponEstimate } from "@/composables/useCouponEstimate.js";

const cartStore = useCartStore();

const route = useRoute();
const router = useRouter();
const furnitureId = ref(route.params.id);
const isFavorited = ref(false);
const savedAddresses = ref([]);
const selectedAddressId = ref(null);
const useNewAddress = ref(false);
// 下单时是否把本次填写的地址存入地址簿。默认勾选（保留原来的便利性），
// 但改成用户可见、可取消的显式开关，不再"静默"往地址簿里写数据。
const saveAddressToBook = ref(true);
const currentImage = ref("");
const mainImgError = ref(false);

const allImages = computed(() => {
  const list = [];
  if (furniture.value?.fIcon) {
    list.push(furniture.value.fIcon);
  }
  if (furniture.value?.images) {
    const extras = furniture.value.images
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    list.push(...extras);
  }
  return list;
});

const currentUserId = ref(null);

// 是否已登录（游客浏览时跳过需登录的接口调用）
const isLoggedIn = computed(() => !!localStorage.getItem("token"));

const {
  furniture,
  loading,
  quantity,
  buyDialogVisible,
  buyLoading,
  buyForm,
  formatPrice,
  decreaseQty,
  increaseQty,
  addToCart,
  closeBuyDialog,
  submitBuy,
  loadFurnitureDetail,
  goHome,
  specGroups,
  skuList,
  selectedSpecs,
  selectedSku,
  hasSpecs,
  displayPrice,
  displayStock,
  displayImage,
  loadSpecs,
  selectSpec,
  isSpecValueAvailable,
} = useFurnitureDetail();

const { goBack } = useBackNavigation();
const { requireLogin } = useRequireLogin();

// ========== 立即购买 - 优惠券（弹窗多选，支持叠加） ==========
const buyCoupons = ref([]);
/** 已选中的 userCouponId 数组 */
const selectedBuyCouponIds = ref([]);
const showBuyCouponDialog = ref(false);

/** 本地估算的商品总额，仅作为试算结果回来之前的展示兜底 */
const buyGoodsTotal = computed(() =>
  Math.round(Number(displayPrice.value || 0) * Number(quantity.value || 1) * 100) / 100,
);

/**
 * 试算用的商品明细。只在「立即购买」弹窗打开时才有值 ——
 * 详情页本身不展示优惠，没必要一进页面就打一次试算接口。
 */
const buyCheckoutItems = computed(() => {
  if (!buyDialogVisible.value) return [];
  const fid = furniture.value?.id;
  if (!fid) return [];
  return [
    {
      furnitureId: fid,
      skuId: selectedSku.value?.id ?? null,
      quantity: Number(quantity.value) || 1,
    },
  ];
});

/**
 * 金额一律由后端试算，前端不再保留任何抵扣算法。
 * 商品/规格/数量或已选券一变就自动重算。
 */
const {
  loaded: estimateLoaded,
  goodsTotal: estimateGoodsTotal,
  discount: buyDiscountEstimate,
  isUsable,
} = useCouponEstimate(
  () => buyCheckoutItems.value,
  () => selectedBuyCouponIds.value,
);

/** 展示用商品总额：优先后端按数据库价格算出的，未算出来时退回本地价，避免闪 ¥0.00 */
const displayBuyTotal = computed(() =>
  estimateLoaded.value ? estimateGoodsTotal.value : buyGoodsTotal.value,
);

/** 已选券对象（按当前券列表还原） */
const selectedBuyCoupons = computed(() =>
  selectedBuyCouponIds.value
    .map((id) => buyCoupons.value.find((c) => c.userCouponId === id))
    .filter(Boolean),
);

/**
 * 可用券数量，用于「暂无可用优惠券」提示。
 * 试算结果没回来时 isUsable 返回 true，此时按「有券可选」展示，不会误报成没有可用券。
 */
const buyAvailableCount = computed(
  () => buyCoupons.value.filter((c) => isUsable(c.userCouponId)).length,
);

const buyCouponText = computed(() => {
  const n = selectedBuyCouponIds.value.length;
  if (!n) return "选择优惠券";
  if (n === 1 && selectedBuyCoupons.value[0]) return selectedBuyCoupons.value[0].name;
  return `已选 ${n} 张券`;
});

const loadBuyCoupons = async () => {
  if (!localStorage.getItem("token")) return;
  const res = await getMyCoupons();
  buyCoupons.value = (res.success || res.code === 200) ? res.data || [] : [];
  // 券列表刷新后，剔除已失效/不再存在的选中项
  const valid = new Set(buyCoupons.value.map((c) => c.userCouponId));
  selectedBuyCouponIds.value = selectedBuyCouponIds.value.filter((id) => valid.has(id));
};

watch(buyDialogVisible, (open) => {
  if (open) {
    selectedBuyCouponIds.value = [];
    // 每次打开下单弹窗都恢复默认勾选，避免上次取消的选择影响下次
    saveAddressToBook.value = true;
    loadBuyCoupons();
    // 优惠金额由 useCouponEstimate 在明细变化时自动试算，这里无需手动触发

  }
});

// 重写 buyNow，在打开对话框时填入已选地址
const buyNow = () => {
  // 未登录引导登录
  if (!requireLogin("下单需要登录")) return;
  if (hasSpecs.value && !selectedSku.value) {
    ElMessage.warning("请先选择规格");
    return;
  }
  if (displayStock.value <= 0) {
    ElMessage.warning("该商品暂时缺货");
    return;
  }
  // 先打开对话框
  buyDialogVisible.value = true;
  // 如果有已保存的地址，填入默认地址
  if (savedAddresses.value.length > 0) {
    const defaultAddr =
      savedAddresses.value.find((a) => a.isDefault === 1) ||
      savedAddresses.value[0];
    selectedAddressId.value = defaultAddr.id;
    buyForm.value.consignee = defaultAddr.consignee;
    buyForm.value.phone = defaultAddr.phone;
    buyForm.value.address = defaultAddr.address;
    useNewAddress.value = false;
  } else {
    // 没有地址，清空表单并显示输入框
    buyForm.value = { consignee: "", phone: "", address: "", remark: "" };
    selectedAddressId.value = null;
    useNewAddress.value = true;
  }
};

const loadAddresses = async () => {
  try {
    const res = await getAddressList();
    if ((res.success || res.code === 200) && Array.isArray(res.data)) {
      savedAddresses.value = res.data;
      // 如果有地址，默认选中默认地址或第一个
      if (savedAddresses.value.length > 0) {
        const defaultAddr =
          savedAddresses.value.find((a) => a.isDefault === 1) ||
          savedAddresses.value[0];
        selectedAddressId.value = defaultAddr.id;
        buyForm.value.consignee = defaultAddr.consignee;
        buyForm.value.phone = defaultAddr.phone;
        buyForm.value.address = defaultAddr.address;
        useNewAddress.value = false;
      } else {
        useNewAddress.value = true;
      }
    }
  } catch (e) {
    /* ignore */
  }
};

const onAddressSelect = (id) => {
  if (id === 0) {
    // 选择"使用新地址"
    useNewAddress.value = true;
    buyForm.value = { consignee: "", phone: "", address: "", remark: "" };
    return;
  }
  if (!id) return;
  const addr = savedAddresses.value.find((a) => a.id === id);
  if (addr) {
    useNewAddress.value = false;
    // 使用展开运算符创建新对象，确保响应式更新
    buyForm.value = {
      consignee: addr.consignee,
      phone: addr.phone,
      address: addr.address,
      remark: "",
    };
  }
};

const goToAddresses = () => {
  router.push("/user/addresses");
};

const handleSubmitBuy = async () => {
  // 必须先快照：submitBuy 内部下单成功后会关闭弹窗并把 buyForm 重置为空，
  // 事后再读 buyForm 只能读到空值，导致保存地址时上报「手机号不能为空」。
  const addrSnapshot = {
    consignee: (buyForm.value.consignee || "").trim(),
    phone: (buyForm.value.phone || "").trim(),
    address: (buyForm.value.address || "").trim(),
  };
  // 勾选状态同样先取快照（表单关闭后状态可能被重置）
  const shouldSave = saveAddressToBook.value;
  // 选的是地址簿里已有的地址时，没必要也不应该再存一份
  const isNewAddress = useNewAddress.value;

  const success = await submitBuy(
    selectedBuyCouponIds.value.length ? [...selectedBuyCouponIds.value] : undefined,
  );
  if (!success) return;

  // 未勾选 / 用的是已有地址 / 地址信息不完整 → 都不写入地址簿
  if (!shouldSave || !isNewAddress) return;
  if (!addrSnapshot.consignee || !addrSnapshot.phone || !addrSnapshot.address) return;

  // 已存在完全相同的地址则不重复保存
  const duplicated = savedAddresses.value.some(
    (a) =>
      a.consignee === addrSnapshot.consignee &&
      a.phone === addrSnapshot.phone &&
      a.address === addrSnapshot.address,
  );
  if (duplicated) return;

  // 地址簿为空时，存的第一条顺手设为默认地址，省得用户再去地址页设置
  const isFirstAddress = savedAddresses.value.length === 0;

  try {
    await saveAddress({
      consignee: addrSnapshot.consignee,
      phone: addrSnapshot.phone,
      address: addrSnapshot.address,
      isDefault: isFirstAddress ? 1 : 0,
    });
  } catch (e) {
    // 地址保存失败不影响主流程
    logger.error("保存地址失败:", e);
  }
};

watch(
  allImages,
  (imgs) => {
    if (imgs.length > 0 && !currentImage.value) {
      currentImage.value = imgs[0];
    }
  },
  { immediate: true },
);

watch(currentImage, () => {
  mainImgError.value = false;
});

// 选中SKU有独立图片时，自动切换到SKU图片
watch(selectedSku, (newSku) => {
  if (newSku && newSku.skuImage) {
    currentImage.value = newSku.skuImage;
  } else if (allImages.value.length > 0) {
    currentImage.value = allImages.value[0];
  }
});

const activeTab = ref("detail");
const relatedProducts = ref([]);
const typeInfo = ref({});

const reviewList = ref([]);
const reviewStats = ref({ avgRating: 0, reviewCount: 0 });
const reviewRatingStars = computed(() =>
  Math.round(Number(reviewStats.value.avgRating) || 0),
);

const goToType = (id) => {
  router.push({ path: `/type/${id}` });
};

const loadRelatedProducts = async () => {
  // 取当前商品的分类来查「相关」商品。
  // 此前传的是 typeId: 0（后端语义为「全部商品」），取到的永远是全站最新 4 件
  // 再排除自身，与当前商品的分类没有任何关系。
  const tid = furniture.value?.typeId ?? furniture.value?.type_id ?? null;
  if (tid == null) {
    // 拿不到分类就不猜，宁可空着也不要退回 0 展示无关商品
    relatedProducts.value = [];
    return;
  }
  try {
    // 多取 1 条：当前商品本身也会命中该分类，过滤掉后仍能凑满 4 个
    const res = await getFurnitureByTypeId({ typeId: tid, current: 1, size: 5 });
    if ((res.success || res.code === 200) && res.data) {
      relatedProducts.value = (res.data.records || [])
        .filter((p) => p.id != furnitureId.value)
        .slice(0, 4);
    }
  } catch {
    /* ignore */
  }
};

const loadTypeInfo = () => {
  const cached = sessionStorage.getItem("currentType");
  if (cached) {
    try {
      const parsed = JSON.parse(cached);
      typeInfo.value = parsed;
    } catch {
      typeInfo.value = {};
    }
  }
};

const loadReviews = async () => {
  try {
    const res = await getComments(furnitureId.value, 1, 100);
    if ((res.success || res.code === 200) && res.data) {
      const records = (res.data.records || res.data || [])
    .filter(r => !r.deleted && !r.userDeleted)
    .map(r => ({
      ...r,
      appendList: (r.appendList || []).filter(a => !a.deleted && !a.userDeleted),
    }));
      reviewList.value = records;
      // 计算平均分
      if (records.length > 0) {
        const totalScore = records.reduce((sum, c) => sum + (c.score || 0), 0);
        reviewStats.value = {
          avgRating: (totalScore / records.length).toFixed(1),
          reviewCount: records.length,
        };
      }
      // 评论数由后端在评价列表里一并返回（commentCount），不再逐条请求
      // /review-comment/list/{id} —— 原先固定 100 条评价就是 100 次串行 HTTP
      for (const r of records) {
        reviewCommentCountMap[r.id] = Number(r.commentCount) || 0;
      }
      // 如果 URL 带了 reviewId，滚动到对应位置
      const targetReviewId = route.query.reviewId;
      const targetReviewCommentId = route.query.reviewCommentId;
      if (targetReviewId) {
        await handleNotificationScroll(
          Number(targetReviewId),
          targetReviewCommentId ? Number(targetReviewCommentId) : null,
        );
      }
    }
  } catch (e) {
    /* ignore */
  }
};

// 通知跳转：打开评价弹窗 → 展开评论区 → 滚动到具体回复并高亮
const handleNotificationScroll = (reviewId, reviewCommentId) => {
  return new Promise((resolve) => {
    // 1. 检查评价是否仍存在（未被用户或管理员删除）
    const targetReview = reviewList.value.find((r) => r.id === reviewId);
    if (
      !targetReview ||
      targetReview.deleted === 1 ||
      targetReview.userDeleted === 1
    ) {
      ElMessage.warning("该评价已被删除");
      resolve();
      return;
    }
    // 2. 打开全部评价弹窗
    reviewDialogVisible.value = true;
    // el-dialog 有 CSS 动画，setTimeout 等到动画结束后再操作 DOM
    setTimeout(async () => {
      try {
        // 2. 滚动到目标评价
        const reviewEl = document.getElementById("review-dialog-" + reviewId);
        if (!reviewEl) {
          resolve();
          return;
        }
        reviewEl.scrollIntoView({ behavior: "smooth", block: "center" });

        if (reviewCommentId) {
          // 3. 展开评论区
          expandedReviews[reviewId] = true;
          // 4. 加载评论数据
          if (!reviewCommentsMap[reviewId]) {
            await loadReviewComments(reviewId);
          }
          await nextTick();
          // 5. 滚动到具体评论并高亮
          const commentEl = document.getElementById(
            "review-comment-" + reviewCommentId,
          );
          if (commentEl) {
            commentEl.scrollIntoView({ behavior: "smooth", block: "center" });
            commentEl.classList.add("review-highlight");
            setTimeout(
              () => commentEl.classList.remove("review-highlight"),
              2000,
            );
          } else {
            ElMessage.info("原评论已被删除");
          }
        } else {
          // 没有评论ID，高亮评价本身
          reviewEl.classList.add("review-highlight");
          setTimeout(() => reviewEl.classList.remove("review-highlight"), 2000);
        }
      } catch (e) {
        logger.error("handleNotificationScroll 出错:", e);
      }
      resolve();
    }, 350); // 等 el-dialog 打开动画完成（默认 ~300ms）
  });
};

const reviewDialogVisible = ref(false);
const showAllReviews = () => {
  reviewDialogVisible.value = true;
};

// 咨询功能
// 图片预览
const imagePreviewVisible = ref(false);
const previewImageUrl = ref("");
const previewImage = (url) => {
  previewImageUrl.value = url;
  imagePreviewVisible.value = true;
};

// 视频预览
const videoPreviewVisible = ref(false);
const previewVideoUrl = ref("");
const previewVideo = (url) => {
  previewVideoUrl.value = url;
  videoPreviewVisible.value = true;
};

const parseImages = (images) => {
  if (!images) return [];
  try {
    const arr = JSON.parse(images);
    return Array.isArray(arr) ? arr : [];
  } catch {
    return images.split(",").filter((img) => img.trim());
  }
};
const parseAppendImages = parseImages;

// ========== 评论区功能 ==========
const expandedReviews = reactive({});
const reviewCommentsMap = reactive({});
const reviewCommentCountMap = reactive({});
const commentInputMap = reactive({});
const commentPlaceholderMap = reactive({});
const commentReplyToMap = reactive({});
const commentSubmittingMap = reactive({});

const toggleComments = async (reviewId) => {
  expandedReviews[reviewId] = !expandedReviews[reviewId];
  // 展开时加载评论内容（如果还没加载）
  if (expandedReviews[reviewId] && !reviewCommentsMap[reviewId]) {
    await loadReviewComments(reviewId);
  }
};

const loadReviewComments = async (reviewId) => {
  try {
    const res = await getReviewComments(reviewId);
    if ((res.success || res.code === 200) && res.data) {
      reviewCommentsMap[reviewId] = filterDeletedComments(res.data || []);
      reviewCommentCountMap[reviewId] = countComments(reviewCommentsMap[reviewId]);
    }
  } catch (e) {
    reviewCommentsMap[reviewId] = [];
    reviewCommentCountMap[reviewId] = 0;
  }
};

const countComments = (list) => {
  let count = 0;
  for (const c of list) {
    count++;
    if (c.children?.length > 0) {
      count += countComments(c.children);
    }
  }
  return count;
};

// 递归过滤已删除的评论（含子评论）
const filterDeletedComments = (list) => {
  return list
    .filter(c => !c.deleted && !c.userDeleted)
    .map(c => ({
      ...c,
      children: c.children ? filterDeletedComments(c.children) : [],
    }));
};

const replyToComment = (review, comment) => {
  commentReplyToMap[review.id] = comment;
  commentPlaceholderMap[review.id] = `回复 ${comment.userName || "用户"}：`;
  commentInputMap[review.id] = "";
};

const cancelReply = (reviewId) => {
  commentReplyToMap[reviewId] = null;
  commentPlaceholderMap[reviewId] = "写评论...";
  commentInputMap[reviewId] = "";
};

const submitReviewComment = async (reviewId, reviewUserId) => {
  // 未登录引导登录
  if (!requireLogin("发表评论需要登录")) return;
  const content = (commentInputMap[reviewId] || "").trim();
  if (!content) {
    ElMessage.warning("请输入评论内容");
    return;
  }
  commentSubmittingMap[reviewId] = true;
  try {
    const replyTo = commentReplyToMap[reviewId];
    // 回复评论时用评论者id，直接评论评价时用评价作者id
    const targetUserId = replyTo ? replyTo.userId : reviewUserId;
    const data = {
      reviewId: reviewId,
      content: content,
      replyToUserId:
        targetUserId && targetUserId !== currentUserId.value
          ? targetUserId
          : null,
      replyToCommentId: replyTo ? replyTo.replyToCommentId || replyTo.id : null,
    };
    const res = await addReviewComment(data);
    if (res.success || res.code === 200) {
      ElMessage.success("评论已提交，审核通过后自动发布");
      commentInputMap[reviewId] = "";
      commentReplyToMap[reviewId] = null;
      commentPlaceholderMap[reviewId] = "写评论...";
      await loadReviewComments(reviewId);
    } else {
      ElMessage.error(res.msg || "评论失败");
    }
  } catch (e) {
    logger.error("评论失败:", e);
  } finally {
    commentSubmittingMap[reviewId] = false;
  }
};

const handleDeleteReviewComment = async (commentId, reviewId) => {
  try {
    await ElMessageBox.confirm("确定删除该评论吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    const res = await deleteReviewComment(commentId);
    if (res.success || res.code === 200) {
      ElMessage.success("删除成功");
      // 重新加载评论并更新计数
      const commentsRes = await getReviewComments(reviewId);
      if (
        (commentsRes.success || commentsRes.code === 200) &&
        commentsRes.data
      ) {
        reviewCommentsMap[reviewId] = filterDeletedComments(commentsRes.data || []);
        reviewCommentCountMap[reviewId] = countComments(reviewCommentsMap[reviewId]);
      }
    }
  } catch (e) {
    if (e !== "cancel") {
      logger.error("删除失败:", e);
    }
  }
};

const handleDeleteReview = async (reviewId) => {
  try {
    await ElMessageBox.confirm("确定删除该评价吗？删除后不可恢复", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    const res = await deleteReview(reviewId);
    if (res.success || res.code === 200) {
      ElMessage.success("删除成功");
      loadReviews();
    }
  } catch (e) {
    if (e !== "cancel") logger.error("删除失败:", e);
  }
};

const handleDeleteAppend = async (appendId, reviewId) => {
  try {
    await ElMessageBox.confirm("确定删除该追评吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    const res = await deleteAppend(appendId);
    if (res.success || res.code === 200) {
      ElMessage.success("删除成功");
      loadReviews();
    }
  } catch (e) {
    if (e !== "cancel") logger.error("删除失败:", e);
  }
};

const handleToggleFav = async () => {
  // 未登录引导登录
  if (!requireLogin("收藏功能需要登录")) return;
  try {
    const res = await toggleFavorite(furnitureId.value);
    if (res.success || res.code === 200) {
      isFavorited.value = res.data;
      ElMessage.success(isFavorited.value ? "已收藏" : "已取消收藏");
    }
  } catch (e) {
    logger.error("操作失败:", e);
  }
};

onMounted(async () => {
  loadUserInfo();
  loadTypeInfo();
  // 详情与规格并行拉取，但要留下详情这个 Promise：
  // loadRelatedProducts 依赖 furniture.typeId，游客路径下从发起详情请求到
  // 调 loadRelatedProducts 之间没有任何 await 点，不 await 就会拿到空对象
  const detailPromise = loadFurnitureDetail(furnitureId.value);
  loadSpecs(furnitureId.value);
  // 已登录才加载地址和收藏状态（游客浏览不触发需登录接口）
  if (isLoggedIn.value) {
    loadAddresses();
    try {
      const res = await checkFavorite(furnitureId.value);
      if (res.success || res.code === 200) {
        isFavorited.value = res.data;
      }
    } catch (e) {
      /* ignore */
    }
  }
  loadReviews();
  await detailPromise;
  loadRelatedProducts();
});

const loadUserInfo = () => {
  const userInfoStr = localStorage.getItem("userInfo");
  if (userInfoStr) {
    try {
      const userInfo = JSON.parse(userInfoStr);
      currentUserId.value = userInfo.id || null;
    } catch (e) {
      /* ignore */
    }
  }
};

const handleImgError = () => {
  mainImgError.value = true;
};

const handleThumbError = (e) => {
  e.target.style.display = "none";
};

const handleSummaryImgError = (e) => {
  e.target.style.display = "none";
  e.target.parentElement.querySelector(".summary-info").style.marginLeft = "0";
};
</script>

<style scoped lang="scss">
@import "@/styles/views/furniture-detail-view.scss";
@import "@/styles/views/cart-drawer.scss";

/* 立即购买弹窗 - 优惠券与应付区域 */
.buy-coupon {
  margin: 4px 0 12px;
  padding: 0 2px;

  .coupon-select-hd {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;

    .label {
      font-size: 14px;
      color: #666;
      flex-shrink: 0;
    }

    .picker {
      flex: 1;
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 12px;
      border: 1px solid #e6e1da;
      border-radius: 6px;
      background: #fff;
      font-size: 14px;
      color: #333;
      cursor: pointer;
      transition: border-color 0.2s;

      &:hover {
        border-color: #c5554a;
      }

      .picker-save {
        color: #c5554a;
        font-style: normal;
        font-weight: 600;
      }

      .arrow {
        color: #999;
        margin-left: 6px;
        font-size: 12px;
      }
    }
  }
}
.buy-total {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  padding: 6px 2px 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #f0ece7;
  .buy-og {
    font-size: 13px;
    color: #999;
  }
  .buy-pay {
    font-size: 14px;
    color: #333;
    b {
      color: #c5554a;
      font-size: 20px;
    }
  }
}
</style>

