<template>
  <div class="orders-page">
    <!-- Breadcrumb -->
    <div class="page-breadcrumb">
      <button class="breadcrumb-back" @click="goBack" title="返回">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
      </button>
      <router-link to="/">首页</router-link>
      <span>/</span>
      <router-link to="/user/profile">个人中心</router-link>
      <span>/</span>
      <span class="current">购买记录</span>
    </div>

    <div class="orders-container">
      <!-- 页面标题 -->
      <div class="page-header">
        <h1>我的订单</h1>
        <p>查看您的历史购买记录及订单详情</p>
      </div>

      <!-- 状态页签 -->
      <el-tabs v-model="activeTab" class="order-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="待支付" name="0" />
        <el-tab-pane label="待发货" name="1" />
        <el-tab-pane label="已发货" name="2" />
        <el-tab-pane label="待评价" name="3" />
        <el-tab-pane label="已完成" name="5" />
        <el-tab-pane label="售后/退款" name="6,7,8" />
      </el-tabs>

      <!-- 订单列表 -->
      <div class="orders-list" v-if="loading">
        <div class="loading-state">
          <div class="spinner"></div>
          <p>加载订单中...</p>
        </div>
      </div>

      <div class="orders-list" v-else-if="orderList.length === 0">
        <el-empty description="暂无订单记录">
          <el-button type="primary" @click="goHome">去逛逛</el-button>
        </el-empty>
      </div>

      <div class="orders-list" v-else>
        <el-card
          v-for="order in orderList"
          :key="order.id"
          class="order-card"
          shadow="hover"
        >
          <!-- 订单头部 -->
          <div class="order-header">
            <div class="order-info">
              <span class="order-no">订单号：{{ order.id }}</span>
              <span class="order-time">{{
                formatTimeFull(order.createTime)
              }}</span>
            </div>
            <el-tag
              :type="getStatusType(order.status)"
              size="small"
              effect="dark"
            >
              {{ getStatusText(order.status) }}
            </el-tag>
          </div>

          <!-- 待支付倒计时 -->
          <div
            v-if="order.status === 0"
            class="order-countdown"
            :class="{
              warning: isOrderWarning(order.id),
              urgent: isOrderUrgent(order.id),
            }"
          >
            <span class="countdown-icon">⏱</span>
            <span v-if="getOrderRemaining(order.id) > 0">
              剩余
              <strong>{{ getOrderCountdown(order.id) }}</strong>
              内完成支付，超时自动取消
            </span>
            <span v-else>订单已超时，即将自动取消...</span>
          </div>

          <!-- 订单商品列表 -->
          <div class="order-items">
            <div
              v-for="item in order.itemList || []"
              :key="item.id"
              class="order-item"
              @click="goToFurniture(item.furnitureId)"
            >
              <img
                :src="
                  imgUrl(item.furnitureIcon, '/images/default-furniture.png')
                "
                class="item-img"
                @error="handleImgError"
              />
              <div class="item-info">
                <h4>{{ item.furnitureName }}</h4>
                <p class="item-spec" v-if="item.skuSpec">{{ item.skuSpec }}</p>
                <p class="item-price">
                  ¥{{ formatPrice(item.price) }} × {{ item.quantity }}
                </p>
              </div>
              <div class="item-total">
                ¥{{ formatPrice(item.itemTotalPrice) }}
              </div>
            </div>
          </div>

          <!-- 订单底部 -->
          <div class="order-footer">
            <div class="delivery-info">
              <p>
                <el-icon>
                  <User />
                </el-icon>
                {{ order.consignee }} {{ order.phone }}
              </p>
              <p>
                <el-icon>
                  <Location />
                </el-icon>
                {{ order.address }}
              </p>
            </div>
            <div class="order-summary">
              <div class="total-row">
                <span>共 {{ getTotalCount(order.itemList || []) }} 件商品</span>
                <span class="total-price"
                  >实付：¥{{ formatPrice(order.totalPrice) }}</span
                >
              </div>
              <div class="order-actions">
                <!-- 待支付：支付 + 取消 -->
                <el-button
                  v-if="order.status === 0"
                  type="primary"
                  size="small"
                  @click="payOrder(order.id)"
                >
                  立即支付
                </el-button>
                <el-button
                  v-if="order.status === 0"
                  type="danger"
                  plain
                  size="small"
                  @click="cancelOrder(order.id)"
                >
                  取消订单
                </el-button>

                <!-- 已发货：确认收货 + 查看详情 -->
                <el-button
                  v-if="order.status === 2"
                  type="success"
                  size="small"
                  @click="confirmReceipt(order)"
                >
                  <el-icon>
                    <Check />
                  </el-icon>
                  确认收货
                </el-button>

                <!-- 已完成：评价 + 查看详情 -->
                <el-button
                  v-if="canReviewOrder(order)"
                  type="warning"
                  size="small"
                  @click="openReviewDialog(order)"
                >
                  <el-icon>
                    <Star />
                  </el-icon>
                  {{ reviewBtnText(order) }}
                </el-button>

                <!-- 已评价：查看评价 -->
                <el-button
                  v-if="order.status === 5"
                  type="success"
                  size="small"
                  @click="openReviewManageDialog(order)"
                >
                  <el-icon>
                    <Star />
                  </el-icon>
                  查看评价
                </el-button>

                <!-- 可退款状态：申请退款 -->
                <el-button
                  v-if="
                    order.status === 1 ||
                    order.status === 2 ||
                    order.status === 3 ||
                    order.status === 5
                  "
                  type="warning"
                  plain
                  size="small"
                  @click="openRefundDialog(order)"
                >
                  申请退款
                </el-button>

                <!-- 退款流程中提示 -->
                <el-tag
                  v-if="order.status === 6 || order.status === 7"
                  type="warning"
                  size="small"
                  class="refund-tag"
                >退款处理中</el-tag>

                <!-- 已发货/已完成/已评价/退款状态：查看详情 -->
                <el-button
                  v-if="
                    order.status === 2 ||
                    order.status === 3 ||
                    order.status === 5 ||
                    order.status === 6 ||
                    order.status === 7 ||
                    order.status === 8
                  "
                  type="info"
                  plain
                  size="small"
                  @click="viewDetail(order)"
                >
                  查看详情
                </el-button>

                <!-- 终态订单：删除 -->
                <el-button
                  v-if="
                    order.status === 3 ||
                    order.status === 4 ||
                    order.status === 5 ||
                    order.status === 8
                  "
                  type="info"
                  plain
                  size="small"
                  @click="handleDeleteOrder(order.id)"
                >
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 分页 -->
        <div class="pagination-wrapper" v-if="total > 0">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[5, 10, 20]"
            layout="total, sizes, prev, pager, next"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </div>
    </div>

    <!-- 评价弹窗 -->
    <el-dialog
      v-model="reviewDialogVisible"
      title="商品评价"
      width="520px"
      :close-on-click-modal="false"
    >
      <div v-if="reviewTarget" class="review-dialog-body">
        <el-form label-position="top">
          <el-form-item label="评价商品">
            <el-select
              v-model="reviewForm.furnitureId"
              placeholder="选择要评价的商品"
              style="width: 100%"
            >
              <el-option
                v-for="item in unreviewedItems(reviewTarget)"
                :key="item.furnitureId"
                :label="item.furnitureName"
                :value="item.furnitureId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="评分">
            <el-rate v-model="reviewForm.score" :max="5" show-score />
          </el-form-item>
          <el-form-item label="评价内容">
            <el-input
              v-model="reviewForm.content"
              type="textarea"
              :rows="4"
              placeholder="分享你的使用体验..."
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="上传图片（最多6张，单张≤5MB）">
            <el-upload
              v-model:file-list="reviewImages"
              action="#"
              list-type="picture-card"
              :limit="6"
              :auto-upload="false"
              :before-upload="beforeImageUpload"
              :on-exceed="handleImageExceed"
              accept="image/*"
            >
              <el-icon>
                <Plus />
              </el-icon>
            </el-upload>
          </el-form-item>
          <el-form-item label="上传视频（最多1个，≤50MB）">
            <el-upload
              v-model:file-list="reviewVideo"
              action="#"
              :limit="1"
              :auto-upload="false"
              :before-upload="beforeVideoUpload"
              :on-exceed="handleVideoExceed"
              accept="video/*"
            >
              <el-button type="primary" plain>
                <el-icon>
                  <Plus />
                </el-icon>
                选择视频
              </el-button>
            </el-upload>
          </el-form-item>
          <el-form-item>
            <el-checkbox
              v-model="reviewForm.isAnonym"
              :true-value="1"
              :false-value="0"
              >匿名评价</el-checkbox
            >
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitReview"
          :loading="reviewSubmitting"
          >提交评价</el-button
        >
      </template>
    </el-dialog>

    <!-- 查看评价弹窗 -->
    <el-dialog
      v-model="reviewManageVisible"
      title="我的评价"
      width="600px"
      :close-on-click-modal="false"
    >
      <div v-if="reviewManageOrder" class="review-manage-body">
        <div v-if="existingReviews.length > 0">
          <div
            v-for="r in existingReviews"
            :key="r.id"
            class="review-manage-card"
          >
            <!-- 已删除的评价占位 -->
            <div
              v-if="r.deleted === 1 || r.userDeleted === 1"
              class="review-deleted-placeholder"
            >
              <span class="deleted-icon">🗑️</span>
              <span>该评价已删除</span>
            </div>
            <template v-else>
              <!-- 主评价 -->
              <div class="review-manage-card-hd">
                <span class="review-manage-stars">{{
                  "⭐".repeat(r.score)
                }}</span>
                <el-tag v-if="r.status === 0" type="warning" size="small"
                  >审核中</el-tag
                >
                <el-tag v-if="r.status === 2" type="danger" size="small"
                  >审核未通过</el-tag
                >
                <span class="review-manage-time">{{
                  formatTimeFull(r.createTime)
                }}</span>
                <el-button
                  text
                  type="danger"
                  size="small"
                  @click="handleDeleteReview(r.id)"
                  style="margin-left: auto"
                >
                  删除
                </el-button>
              </div>
              <p class="review-manage-content" v-if="r.content">
                {{ r.content }}
              </p>
              <!-- 主评价图片 -->
              <div
                v-if="parseImages(r.imgUrl).length > 0"
                class="review-images"
              >
                <img
                  v-for="(img, idx) in parseImages(r.imgUrl)"
                  :key="idx"
                  :src="img"
                  class="review-img"
                  @click="previewImage(img)"
                />
              </div>
              <!-- 主评价视频 -->
              <div v-if="r.videoUrl" class="review-video">
                <video
                  :src="r.videoUrl"
                  controls
                  class="review-video-player"
                ></video>
              </div>

              <!-- 已有追评 -->
              <div
                v-for="a in r.appendList || []"
                :key="a.id"
                class="append-item"
              >
                <div class="append-hd">
                  <span class="append-tag"
                    >追评{{ a.appendNum === 1 ? "" : a.appendNum }}</span
                  >
                  <el-tag v-if="a.status === 0" type="warning" size="small"
                    >审核中</el-tag
                  >
                  <el-tag v-if="a.status === 2" type="danger" size="small"
                    >审核未通过</el-tag
                  >
                  <el-button
                    v-if="a.userId === currentUserId"
                    text
                    type="danger"
                    size="small"
                    @click="handleDeleteAppend(a.id)"
                    style="margin-left: auto"
                    >删除
                  </el-button>
                  <span v-else class="append-time">{{
                    formatTimeFull(a.appendTime)
                  }}</span>
                </div>
                <p class="append-text">{{ a.appendContent }}</p>
                <div
                  v-if="parseImages(a.appendImg).length > 0"
                  class="review-images"
                >
                  <img
                    v-for="(img, idx) in parseImages(a.appendImg)"
                    :key="idx"
                    :src="img"
                    class="review-img"
                    @click="previewImage(img)"
                  />
                </div>
              </div>

              <!-- 追评按钮 -->
              <div class="append-action">
                <el-button
                  type="primary"
                  plain
                  size="small"
                  @click="startAppend(r)"
                >
                  <el-icon>
                    <EditPen />
                  </el-icon>
                  追评
                </el-button>
              </div>
            </template>

            <!-- 追评表单（点击追评按钮后展开） -->
            <div v-if="appendingCommentId === r.id" class="append-form-wrapper">
              <el-form label-position="top" size="small">
                <el-form-item label="追评内容">
                  <el-input
                    v-model="reviewManageForm.appendContent"
                    type="textarea"
                    :rows="3"
                    placeholder="补充你的使用体验..."
                    maxlength="500"
                    show-word-limit
                  />
                </el-form-item>
                <el-form-item label="上传图片（最多3张，单张≤5MB）">
                  <el-upload
                    v-model:file-list="appendImages"
                    action="#"
                    list-type="picture-card"
                    :limit="3"
                    :auto-upload="false"
                    :before-upload="beforeImageUpload"
                    :on-exceed="handleAppendImageExceed"
                    accept="image/*"
                  >
                    <el-icon>
                      <Plus />
                    </el-icon>
                  </el-upload>
                </el-form-item>
                <div class="append-form-actions">
                  <el-button @click="cancelAppend">取消</el-button>
                  <el-button
                    type="primary"
                    @click="submitAppendReview"
                    :loading="reviewManageSubmitting"
                  >
                    提交追评
                  </el-button>
                </div>
              </el-form>
            </div>

            <el-divider
              v-if="existingReviews.indexOf(r) < existingReviews.length - 1"
            />
          </div>
        </div>
        <el-empty
          v-if="existingReviews.length === 0"
          description="暂无评价数据"
        />
      </div>
    </el-dialog>

    <!-- 申请退款弹窗 -->
    <el-dialog
      v-model="refundDialogVisible"
      title="申请退款"
      width="450px"
      :close-on-click-modal="false"
    >
      <div v-if="refundTarget" class="refund-dialog-body">
        <div class="refund-order-info">
          <p>订单号：{{ refundTarget.id }}</p>
          <p>
            实付金额：
            <span class="refund-amount"
              >¥{{ formatPrice(refundTarget.totalPrice) }}</span
            >
          </p>
        </div>
        <el-form label-position="top">
          <el-form-item
            label="退款原因"
            :rules="[{ required: true, message: '请填写退款原因' }]"
          >
            <el-input
              v-model="refundForm.reason"
              type="textarea"
              :rows="3"
              placeholder="请描述退款原因（如：商品与描述不符、质量问题等）"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitRefund"
          :loading="refundSubmitting"
        >
          提交申请
        </el-button>
      </template>
    </el-dialog>

    <!-- 订单详情弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="订单详情"
      width="600px"
      :close-on-click-modal="false"
    >
      <div v-if="currentOrder" class="order-detail-content">
        <div class="detail-section">
          <h4>订单信息</h4>
          <p><span>订单编号：</span>{{ currentOrder.id }}</p>
          <p>
            <span>下单时间：</span>{{ formatTimeFull(currentOrder.createTime) }}
          </p>
          <p>
            <span>订单状态：</span>
            <el-tag :type="getStatusType(currentOrder.status)" size="small">
              {{ getStatusText(currentOrder.status) }}
            </el-tag>
          </p>
          <p v-if="currentOrder.payTime">
            <span>支付时间：</span>{{ formatTimeFull(currentOrder.payTime) }}
          </p>
          <p v-if="currentOrder.shipTime">
            <span>发货时间：</span>{{ formatTimeFull(currentOrder.shipTime) }}
          </p>
          <p v-if="currentOrder.receiveTime">
            <span>收货时间：</span
            >{{ formatTimeFull(currentOrder.receiveTime) }}
          </p>
        </div>

        <el-divider />

        <div class="detail-section">
          <h4>收货信息</h4>
          <p><span>收货人：</span>{{ currentOrder.consignee }}</p>
          <p><span>联系电话：</span>{{ currentOrder.phone }}</p>
          <p><span>收货地址：</span>{{ currentOrder.address }}</p>
          <p v-if="currentOrder.remark">
            <span>订单备注：</span>{{ currentOrder.remark }}
          </p>
        </div>

        <!-- 退款信息 -->
        <template v-if="[6, 7, 8].includes(currentOrder.status)">
          <el-divider />
          <div class="detail-section refund-detail">
            <h4>退款信息</h4>
            <p v-if="currentOrder.refundReason">
              <span>退款原因：</span>{{ currentOrder.refundReason }}
            </p>
            <p v-if="currentOrder.refundApplyTime">
              <span>申请时间：</span
              >{{ formatTimeFull(currentOrder.refundApplyTime) }}
            </p>
            <p v-if="currentOrder.refundApproveTime">
              <span>受理时间：</span
              >{{ formatTimeFull(currentOrder.refundApproveTime) }}
            </p>
            <p v-if="currentOrder.refundAuditTime">
              <span>审核时间：</span
              >{{ formatTimeFull(currentOrder.refundAuditTime) }}
            </p>
            <p v-if="currentOrder.refundHandleRemark">
              <span>处理备注：</span>{{ currentOrder.refundHandleRemark }}
            </p>
          </div>
        </template>

        <el-divider />

        <div class="detail-section">
          <h4>商品明细</h4>
          <div
            v-for="item in currentOrder?.itemList || []"
            :key="item.id"
            class="detail-item"
          >
            <img
              :src="imgUrl(item.furnitureIcon, '/images/default-furniture.png')"
              class="detail-item-img"
            />
            <div class="detail-item-info">
              <p class="name">{{ item.furnitureName }}</p>
              <p class="spec" v-if="item.skuSpec">{{ item.skuSpec }}</p>
              <p class="price">
                ¥{{ formatPrice(item.price) }} × {{ item.quantity }}
              </p>
            </div>
            <div class="detail-item-total">
              ¥{{ formatPrice(item.itemTotalPrice) }}
            </div>
          </div>
        </div>

        <el-divider />

        <div class="detail-summary">
          <div class="summary-row">
            <span>商品总额</span>
            <span>¥{{ formatPrice(Number(currentOrder.totalPrice) + Number(currentOrder.couponDiscount || 0)) }}</span>
          </div>
          <div class="summary-row">
            <span>运费</span>
            <span>¥0.00</span>
          </div>
          <div class="summary-row" v-if="Number(currentOrder.couponDiscount || 0) > 0">
            <span>优惠券抵扣</span>
            <span class="discount">-¥{{ formatPrice(currentOrder.couponDiscount) }}</span>
          </div>
          <div class="summary-row total">
            <span>实付金额</span>
            <span class="amount"
              >¥{{ formatPrice(currentOrder.totalPrice) }}</span
            >
          </div>
        </div>

        <!-- 弹窗底部操作按钮 -->
        <div v-if="currentOrder.status === 2" class="dialog-actions">
          <el-button
            type="success"
            @click="
              confirmReceipt(currentOrder);
              detailDialogVisible = false;
            "
          >
            <el-icon>
              <Check />
            </el-icon>
            确认收货
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import {
  ArrowLeft,
  Check,
  Delete,
  EditPen,
  Location,
  Plus,
  Star,
  User,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { imgUrl } from "@/utils/img.js";
import { formatPrice, formatTimeFull } from "@/utils/format.js";
import { logger } from "@/utils/logger.js";
import {
  applyRefund as apiApplyRefund,
  cancelOrder as apiCancelOrder,
  confirmReceipt as apiConfirmReceipt,
  deleteOrder as apiDeleteOrder,
  getUserOrders,
} from "@/api/order.js";
import { getFurnitureById } from "@/api/furniture.js";
import { useBackNavigation } from '@/composables/useBackNavigation.js';
import {
  addComment,
  appendComment,
  deleteAppend,
  deleteReview,
  getCommentsByOrderId,
  uploadCommentImage,
  uploadCommentVideo,
} from "@/api/comment.js";

const router = useRouter();

const loading = ref(true);
const orderList = ref([]);
const currentPage = ref(1);
const pageSize = ref(5);
const total = ref(0);

const detailDialogVisible = ref(false);
const currentOrder = ref(null);

// ========== 状态页签 ==========
const activeTab = ref("");

// ========== 申请退款 ==========
const refundDialogVisible = ref(false);
const refundTarget = ref(null);
const refundForm = ref({ reason: "" });
const refundSubmitting = ref(false);

const reviewDialogVisible = ref(false);
const reviewTarget = ref(null);
const reviewForm = ref({
  furnitureId: null,
  score: 5,
  content: "",
  imgUrl: "",
  videoUrl: "",
  isAnonym: 0,
});
const reviewSubmitting = ref(false);
const reviewedMap = reactive({});
const reviewImages = ref([]);
const reviewVideo = ref([]);

const reviewManageVisible = ref(false);
const reviewManageOrder = ref(null);
const reviewManageForm = ref({ appendContent: "" });
const reviewManageSubmitting = ref(false);
const existingReviews = ref([]);
const appendImages = ref([]);
const appendingCommentId = ref(null);

// ========== 支付倒计时 ==========
const PAYMENT_TIMEOUT_MINUTES = 1440; // 24小时，与后端 order.payment-timeout-minutes 保持一致
const orderRemaining = reactive({}); // { orderId: remainingMilliseconds }
let countdownTimer = null;

// 计算订单截止时间
const orderDeadline = (order) => {
  if (!order?.createTime) return null;
  const created = new Date(order.createTime.replace(" ", "T"));
  return new Date(created.getTime() + PAYMENT_TIMEOUT_MINUTES * 60 * 1000);
};

// 更新所有待支付订单的剩余毫秒数（50ms 间隔）
const tickAll = () => {
  let hasActive = false;
  orderList.value.forEach((order) => {
    if (order.status !== 0) {
      delete orderRemaining[order.id];
      return;
    }
    const dl = orderDeadline(order);
    if (!dl) return;
    const diff = dl.getTime() - Date.now();
    orderRemaining[order.id] = Math.max(0, diff);
    if (diff > 0) hasActive = true;
  });
  if (!hasActive && countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
};

const getOrderRemaining = (orderId) => orderRemaining[orderId] ?? 0;

const getOrderCountdown = (orderId) => {
  const ms = orderRemaining[orderId] ?? 0;
  if (ms <= 0) return "00:00:00.00";
  const ts = ms / 1000;
  const h = Math.floor(ts / 3600);
  const m = Math.floor((ts % 3600) / 60);
  const s = Math.floor(ts % 60);
  const cs = Math.floor((ts % 1) * 100);
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}.${String(cs).padStart(2, "0")}`;
};

const isOrderUrgent = (orderId) => {
  const ms = orderRemaining[orderId] ?? 0;
  return ms > 0 && ms <= 600_000;
};

const isOrderWarning = (orderId) => {
  const ms = orderRemaining[orderId] ?? 0;
  return ms > 600_000 && ms <= 3_600_000;
};

const canReviewOrder = (order) => {
  if (order.status !== 3) return false;
  const items = order.itemList || [];
  if (items.length === 0) return false;
  const reviewed = reviewedMap[order.id] || new Set();
  return items.some((item) => !reviewed.has(item.furnitureId));
};

const reviewBtnText = (order) => {
  const reviewed = reviewedMap[order.id];
  return reviewed && reviewed.size > 0 ? "继续评价" : "去评价";
};

// 加载订单列表
const loadOrders = async () => {
  loading.value = true;
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
    };
    if (activeTab.value) {
      params.status = activeTab.value; // "6,7,8" 或 "0"/"1"/...
    }
    const res = await getUserOrders(params);
    if (res.success || res.code === 200) {
      orderList.value = res.data.records || [];
      total.value = res.data.total || 0;
      // 启动倒计时（50ms 刷新，百分秒可见）
      tickAll();
      if (!countdownTimer) {
        countdownTimer = setInterval(tickAll, 50);
      }
    } else {
      ElMessage.error(res.msg || "获取订单失败");
    }
  } catch (error) {
    logger.error("加载订单失败:", error);
  } finally {
    loading.value = false;
  }
};

// 页签切换
const handleTabChange = () => {
  currentPage.value = 1;
  loadOrders();
};

// 状态映射
const getStatusText = (status) => {
  const map = {
    0: "待支付",
    1: "已支付",
    2: "已发货",
    3: "已完成",
    4: "已取消",
    5: "已评价",
    6: "申请退款中",
    7: "退款审核中",
    8: "已退款",
  };
  return map[status] || "未知状态";
};

const getStatusType = (status) => {
  const map = {
    0: "warning",
    1: "success",
    2: "primary",
    3: "info",
    4: "danger",
    5: "success",
    6: "warning",
    7: "primary",
    8: "info",
  };
  return map[status] || "info";
};

// 计算商品总数
const getTotalCount = (items) => {
  if (!items || !Array.isArray(items)) return 0;
  return items.reduce((sum, item) => sum + item.quantity, 0);
};

// 跳转到商品详情
const goToFurniture = (id) => {
  router.push(`/furniture/detail/${id}`);
  getFurnitureById(id);
};

// 支付订单
const payOrder = (orderId) => {
  router.push(`/order/pay/${orderId}`);
};

// 取消订单
const cancelOrder = async (orderId) => {
  try {
    await ElMessageBox.confirm("确定要取消该订单吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    const res = await apiCancelOrder(orderId);
    if (res.success) {
      ElMessage.success("订单已取消");
      loadOrders();
    } else {
      ElMessage.error(res.msg || "取消失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      logger.error("取消订单失败:", error);
    }
  }
};

// 删除订单
const handleDeleteOrder = async (orderId) => {
  try {
    await ElMessageBox.confirm(
      "删除后您将无法查看订单信息，确认删除？",
      "提示",
      {
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    const res = await apiDeleteOrder(orderId);
    if (res.success || res.code === 200) {
      ElMessage.success("订单已删除");
      loadOrders();
    } else {
      ElMessage.error(res.msg || "删除失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      logger.error("删除订单失败:", error);
    }
  }
};

// ========== 申请退款 ==========
const openRefundDialog = (order) => {
  refundTarget.value = order;
  refundForm.value = { reason: "" };
  refundDialogVisible.value = true;
};

const submitRefund = async () => {
  if (!refundForm.value.reason.trim()) {
    ElMessage.warning("请填写退款原因");
    return;
  }
  refundSubmitting.value = true;
  try {
    const res = await apiApplyRefund({
      orderId: refundTarget.value.id,
      refundReason: refundForm.value.reason.trim(),
    });
    if (res.success || res.code === 200) {
      ElMessage.success("退款申请已提交，请等待审核");
      refundDialogVisible.value = false;
      loadOrders();
    } else {
      ElMessage.error(res.msg || res.message || "提交失败");
    }
  } catch (error) {
    logger.error("申请退款失败:", error);
    ElMessage.error("提交失败，请重试");
  } finally {
    refundSubmitting.value = false;
  }
};

// 确认收货
const confirmReceipt = async (order) => {
  try {
    await ElMessageBox.confirm(
      `确认已收到订单 "${order.id}" 的商品吗？`,
      "确认收货",
      {
        confirmButtonText: "确认收货",
        cancelButtonText: "取消",
        type: "success",
      },
    );

    const res = await apiConfirmReceipt(order.id);
    if (res.success || res.code === 200) {
      ElMessage.success("确认收货成功");
      loadOrders(); // 刷新列表
    } else {
      ElMessage.error(res.msg || "确认收货失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      logger.error("确认收货失败:", error);
    }
  }
};

// 查看详情
const viewDetail = (order) => {
  currentOrder.value = order;
  detailDialogVisible.value = true;
};

// 分页处理
const handleSizeChange = (val) => {
  pageSize.value = val;
  currentPage.value = 1;
  loadOrders();
};

const handleCurrentChange = (val) => {
  currentPage.value = val;
  loadOrders();
};

const unreviewedItems = (order) => {
  const reviewed = reviewedMap[order.id] || new Set();
  return (order.itemList || []).filter(
    (item) => !reviewed.has(item.furnitureId),
  );
};

const openReviewDialog = (order) => {
  reviewTarget.value = order;
  const items = unreviewedItems(order);
  reviewForm.value = {
    furnitureId: items.length > 0 ? items[0].furnitureId : null,
    score: 5,
    content: "",
    imgUrl: "",
    videoUrl: "",
    isAnonym: 0,
  };
  reviewImages.value = [];
  reviewVideo.value = [];
  reviewDialogVisible.value = true;
};

// 图片上传前校验
const beforeImageUpload = (file) => {
  const isImage = file.type.startsWith("image/");
  const isLt5M = file.size / 1024 / 1024 < 5;
  if (!isImage) {
    ElMessage.error("只能上传图片文件");
    return false;
  }
  if (!isLt5M) {
    ElMessage.error("图片大小不能超过 5MB");
    return false;
  }
  return true;
};

const handleImageExceed = () => {
  ElMessage.warning("最多上传 6 张图片");
};

// 视频上传前校验
const beforeVideoUpload = (file) => {
  const isVideo = file.type.startsWith("video/");
  const isLt50M = file.size / 1024 / 1024 < 50;
  if (!isVideo) {
    ElMessage.error("只能上传视频文件");
    return false;
  }
  if (!isLt50M) {
    ElMessage.error("视频大小不能超过 50MB");
    return false;
  }
  return true;
};

const handleVideoExceed = () => {
  ElMessage.warning("最多上传 1 个视频");
};

const handleAppendImageExceed = () => {
  ElMessage.warning("追评最多上传 3 张图片");
};

// 上传图片到服务器并返回 URL 列表
const uploadImages = async (files) => {
  const urls = [];
  for (const file of files) {
    try {
      const rawFile = file.raw || file;
      const res = await uploadCommentImage(rawFile);
      if (res.success || res.code === 200) {
        urls.push(res.data);
      }
    } catch (e) {
      logger.error("上传图片失败:", e);
    }
  }
  return urls;
};

// 上传视频到服务器并返回 URL
const uploadVideo = async (files) => {
  if (files.length === 0) return "";
  try {
    const rawFile = files[0].raw || files[0];
    const res = await uploadCommentVideo(rawFile);
    if (res.success || res.code === 200) {
      return res.data;
    }
  } catch (e) {
    logger.error("上传视频失败:", e);
  }
  return "";
};

const submitReview = async () => {
  if (!reviewForm.value.furnitureId) {
    ElMessage.warning("请选择要评价的商品");
    return;
  }
  if (!reviewForm.value.content.trim()) {
    ElMessage.warning("请输入评价内容");
    return;
  }
  reviewSubmitting.value = true;
  try {
    // 上传图片
    let imgUrl = "";
    if (reviewImages.value.length > 0) {
      const urls = await uploadImages(reviewImages.value);
      if (urls.length > 0) {
        imgUrl = JSON.stringify(urls);
      }
    }

    // 上传视频
    let videoUrl = "";
    if (reviewVideo.value.length > 0) {
      videoUrl = (await uploadVideo(reviewVideo.value)) || "";
    }

    const res = await addComment({
      orderId: reviewTarget.value.id,
      goodsId: reviewForm.value.furnitureId,
      score: reviewForm.value.score,
      content: reviewForm.value.content.trim(),
      imgUrl: imgUrl,
      videoUrl: videoUrl,
      isAnonym: reviewForm.value.isAnonym,
    });
    if (res.success || res.code === 200) {
      ElMessage.success("评价已提交，审核通过后自动发布");
      const orderId = reviewTarget.value.id;
      const fid = reviewForm.value.furnitureId;
      if (!reviewedMap[orderId]) reviewedMap[orderId] = new Set();
      reviewedMap[orderId].add(fid);
      reviewDialogVisible.value = false;
      loadOrders();
    } else {
      ElMessage.error(res.msg || "评价失败");
    }
  } catch (e) {
    logger.error("submitReview:", e);
  } finally {
    reviewSubmitting.value = false;
  }
};

const openReviewManageDialog = async (order) => {
  reviewManageOrder.value = order;
  reviewManageForm.value = { appendContent: "" };
  appendImages.value = [];
  existingReviews.value = [];
  appendingCommentId.value = null;
  reviewManageVisible.value = true;
  try {
    const res = await getCommentsByOrderId(order.id);
    if ((res.success || res.code === 200) && res.data) {
      existingReviews.value = res.data;
    }
  } catch (e) {
    existingReviews.value = [];
  }
};

// 解析图片JSON字符串为数组
const parseImages = (imgUrl) => {
  if (!imgUrl) return [];
  try {
    const parsed = JSON.parse(imgUrl);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

// 预览图片
const previewImage = (url) => {
  window.open(url, "_blank");
};

// 点击追评按钮
const startAppend = (review) => {
  appendingCommentId.value = review.id;
  reviewManageForm.value = { appendContent: "" };
  appendImages.value = [];
};

// 取消追评
const cancelAppend = () => {
  appendingCommentId.value = null;
  reviewManageForm.value = { appendContent: "" };
  appendImages.value = [];
};

const submitAppendReview = async () => {
  if (!appendingCommentId.value) {
    ElMessage.warning("请选择要追评的评价");
    return;
  }
  if (!reviewManageForm.value.appendContent.trim()) {
    ElMessage.warning("请输入追评内容");
    return;
  }
  reviewManageSubmitting.value = true;
  try {
    // 上传图片
    let appendImg = "";
    if (appendImages.value.length > 0) {
      const urls = await uploadImages(appendImages.value);
      if (urls.length > 0) {
        appendImg = JSON.stringify(urls);
      }
    }

    const res = await appendComment({
      mainCommentId: appendingCommentId.value,
      appendContent: reviewManageForm.value.appendContent.trim(),
      appendImg: appendImg,
    });
    if (res.success || res.code === 200) {
      ElMessage.success("追评已提交，审核通过后自动发布");
      await openReviewManageDialog(reviewManageOrder.value);
      loadOrders();
    } else {
      ElMessage.error(res.msg || "追评失败");
    }
  } catch (e) {
    logger.error("submitAppendReview:", e);
  } finally {
    reviewManageSubmitting.value = false;
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
      await openReviewManageDialog(reviewManageOrder.value);
      loadOrders();
    }
  } catch (e) {
    if (e !== "cancel") logger.error("handleDeleteReview:", e);
  }
};

const handleDeleteAppend = async (appendId) => {
  try {
    await ElMessageBox.confirm("确定删除该追评吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    const res = await deleteAppend(appendId);
    if (res.success || res.code === 200) {
      ElMessage.success("删除成功");
      await openReviewManageDialog(reviewManageOrder.value);
    }
  } catch (e) {
    if (e !== "cancel") logger.error("handleDeleteAppend:", e);
  }
};

const { goBack } = useBackNavigation();

const goHome = () => {
  router.push("/");
};

const handleImgError = (e) => {
  e.target.src = "/images/default-furniture.png";
};

onMounted(() => {
  loadOrders();
});

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
});
</script>

<style scoped lang="scss">
@import "@/styles/views/user-orders-view.scss";
</style>
