package com.woocommerce.android.ui.orders

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.card.MaterialCardView
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.model.Order
import com.woocommerce.android.tools.ProductImageMap
import com.woocommerce.android.widgets.AlignedDividerDecoration
import kotlinx.android.synthetic.main.order_detail_product_list.view.*
import org.json.JSONArray
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.fluxc.network.rest.wpcom.wc.order.CoreOrderStatus
import java.lang.Exception
import java.math.BigDecimal
import java.text.NumberFormat

class OrderDetailProductListView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(ctx, attrs, defStyleAttr) {
    init {
        View.inflate(context, R.layout.order_detail_product_list, this)
    }
    private lateinit var viewAdapter: OrderDetailProductListAdapter
    private var isExpanded = false

    /**
     * Initialize and format this view.
     *
     * @param [orderModel] The order
     * @param [orderItems] list of products to display.
     * @param [productImageMap] Images for products.
     * @param [expanded] If true, expanded view will be shown, else collapsed view.
     * @param [formatCurrencyForDisplay] Function to use for formatting currencies for display.
     * @param [orderListener] Listener for routing order click actions. If null, the buttons will be hidden.
     * @param [productListener] Listener for routing product click actions.
     * @param [listTitle] title text for the product list
     */
    fun initView(
        orderModel: WCOrderModel,
        orderItems: List<Order.Item>,
        productImageMap: ProductImageMap,
        expanded: Boolean,
        formatCurrencyForDisplay: (BigDecimal) -> String,
        orderListener: OrderActionListener? = null,
        productListener: OrderProductActionListener? = null,
        listTitle: String? = null,
        printDataProductList: MutableList<String>
    ) {

        val metaList = jsonParserForMeta(orderModel.lineItems)
        isExpanded = expanded

        val viewManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        viewAdapter = OrderDetailProductListAdapter(
            orderItems,
            productImageMap,
            formatCurrencyForDisplay,
            isExpanded,
            productListener,
            metaList
        )
        val numberFormatter = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 2
        }

        printDataProductList.clear()

        for(i  in 0 until orderItems.size)
        {
            val orderTotal = formatCurrencyForDisplay(orderItems[i].total)
            val productPrice = formatCurrencyForDisplay(orderItems[i].price)

            var metaString = "\n"
            if(metaList[i].metaList.size > 0) {
                for(item in metaList[i].metaList)
                {
                    metaString +=  item.key + " " + item.value + "\n"
                }
            }

            printDataProductList.add(
                orderItems[i].name + ": " + numberFormatter.format(orderItems[i].quantity)
                    + ", "
                    + context.getString(
                    R.string.orderdetail_product_lineitem_total_qty_and_price,
                    orderTotal, orderItems[i].quantity.toString(), productPrice
                    )
                    + ", "
                    + context.getString(
                    R.string.orderdetail_product_lineitem_sku_value,
                    orderItems[i].sku
                    )
                    + ", Total Tax: "
                    + formatCurrencyForDisplay(
                    orderItems[i].totalTax
                    )
                    + metaString
            )
        }

//        for(item in orderItems)
//        {
//            val orderTotal = formatCurrencyForDisplay(item.total)
//            val productPrice = formatCurrencyForDisplay(item.price)
//            printDataProductList.add(
//                item.name + ": " + numberFormatter.format(item.quantity)
//                    + ", "
//                    + context.getString(
//                    R.string.orderdetail_product_lineitem_total_qty_and_price,
//                    orderTotal, item.quantity.toString(), productPrice
//                    )
//                    + ", "
//                    + context.getString(
//                    R.string.orderdetail_product_lineitem_sku_value,
//                    item.sku
//                    )
//                    + ", Total Tax: "
//                    + formatCurrencyForDisplay(
//                    item.totalTax
//                    )
//            )

//            printDataProductList.add(numberFormatter.format(item.quantity))
//            printDataProductList.add(context.getString(R.string.orderdetail_product_lineitem_sku_value, item.sku))
//            printDataProductList.add(context.getString(
//                R.string.orderdetail_product_lineitem_total_qty_and_price,
//                orderTotal, item.quantity.toString(), productPrice
//            ))
//            printDataProductList.add(formatCurrencyForDisplay(item.totalTax))
//        }


        productList_lblProduct.text = listTitle ?: if (orderItems.size > 1) {
            context.getString(R.string.orderdetail_product_multiple)
        } else {
            context.getString(R.string.orderdetail_product)
        }

        productList_products.apply {
            setHasFixedSize(false)
            layoutManager = viewManager
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
            adapter = viewAdapter

            if (itemDecorationCount == 0) {
                addItemDecoration(
                    AlignedDividerDecoration(
                        context,
                        DividerItemDecoration.VERTICAL,
                        R.id.productInfo_name,
                        padding = context.resources.getDimensionPixelSize(R.dimen.major_100)
                    )
                )
            }

            // Setting this field to false ensures that the RecyclerView children do NOT receive the multiple clicks,
            // and only processes the first click event. More details on this issue can be found here:
            // https://github.com/woocommerce/woocommerce-android/issues/2074
            isMotionEventSplittingEnabled = false
        }

        updateView(orderModel, orderListener)
    }

    private fun jsonParserForMeta(strJson: String): MutableList<MetaItem>
    {
        var metaList = mutableListOf<MetaItem>()

        try{
            var reader = JSONArray(strJson)
            for(j in 0 until reader.length())
            {
                var jasonObj = reader.getJSONObject(j)
                val metaItemList = jasonObj.getJSONArray("meta_data")
                var metaItem = MetaItem()
                var itemBeanList = mutableListOf<MetaItem.ItemBean>()
                for (i in 0 until metaItemList.length()) {
                    val item = metaItemList.getJSONObject(i)

                    val _id = item.getLong("id");
                    val _key = item.getString("key")
                    val _value = item.getString("value")
                    if(_key.contains("<span") || _value.contains("<span"))
                    {
                        var itemBean = MetaItem.ItemBean()
                        itemBean.id = item.getLong("id");
                        itemBean.key = android.text.Html.fromHtml(item.getString("key")).toString()
                        itemBean.value = android.text.Html.fromHtml(item.getString("value")).toString()
                        itemBeanList.add(itemBean)
                    }

                }
                metaItem.metaList = itemBeanList
                metaList.add(metaItem)
            }
            return metaList
        }catch (e: Exception)
        {
            metaList.clear()
            return metaList
        }
    }

    fun updateView(orderModel: WCOrderModel, orderListener: OrderActionListener? = null) {
        orderListener?.let {
            if (orderModel.status == CoreOrderStatus.PROCESSING.value) {
                productList_btnFulfill.visibility = View.VISIBLE
                productList_btnDetails.visibility = View.GONE
                productList_btnDetails.setOnClickListener(null)
                productList_btnFulfill.setOnClickListener {
                    AnalyticsTracker.track(Stat.ORDER_DETAIL_FULFILL_ORDER_BUTTON_TAPPED)
                    orderListener.openOrderFulfillment(orderModel)
                }
            } else {
                productList_btnFulfill.visibility = View.GONE
                productList_btnDetails.visibility = View.VISIBLE
                productList_btnDetails.setOnClickListener {
                    AnalyticsTracker.track(Stat.ORDER_DETAIL_PRODUCT_DETAIL_BUTTON_TAPPED)
                    orderListener.openOrderProductList(orderModel)
                }
                productList_btnFulfill.setOnClickListener(null)
            }
        } ?: hideButtons()
    }

    // called when a product is fetched to ensure we show the correct product image
    fun refreshProductImages() {
        if (::viewAdapter.isInitialized) {
            viewAdapter.notifyDataSetChanged()
        }
    }

    private fun hideButtons() {
        productList_btnFulfill.setOnClickListener(null)
        productList_btnDetails.setOnClickListener(null)
        productList_btnFulfill.visibility = View.GONE
        productList_btnDetails.visibility = View.GONE
    }
}
