package metcap;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;
import org.w3c.dom.events.Event;

import com.sap.scco.ap.plugin.BasePlugin;
//import com.sap.scco.ap.plugin.annotation.ListenToExit;
import com.sap.scco.ap.pos.dao.CDBSession;
import com.sap.scco.ap.pos.dao.CDBSessionFactory;
import com.sap.scco.ap.pos.entity.BaseEntity.EntityActions;
import com.sap.scco.ap.pos.entity.MaterialEntity;
import com.sap.scco.ap.pos.entity.PaymentItemEntity;
import com.sap.scco.ap.pos.entity.ReceiptEntity;
//import com.sap.scco.ap.pos.dto.ReceiptPrintDTO;
import com.sap.scco.ap.pos.entity.SalesItemEntity;
import com.sap.scco.ap.pos.entity.SalesItemTaxItemEntity;
import com.sap.scco.ap.pos.entity.UserEntity;
import com.sap.scco.ap.pos.entity.coupon.AccountCouponEntity;
import com.sap.scco.ap.pos.entity.coupon.CouponAssignmentEntity;
import com.sap.scco.ap.pos.service.CalculationPosService;
//import com.sap.scco.ap.pos.entity.coupon.CouponAssignmentEntity;
import com.sap.scco.ap.pos.service.ReceiptChangeNotifierPosService;
import com.sap.scco.ap.pos.service.ServiceFactory;
import com.sap.scco.ap.pos.service.CalculationPosService;
import com.sap.scco.ap.pos.service.ReceiptChangeNotifierPosService;
import com.sap.scco.ap.pos.service.component.listener.ReceiptChangeListener;
import com.sap.scco.ap.test.BigDecimalAssert;
import com.sap.scco.ap.pos.dao.ReceiptManager;

// discounts
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

// update UI 20240426
import com.sap.scco.env.UIEventDispatcher;
import com.sap.scco.util.CConst;

public class DescountsAddon extends BasePlugin implements ReceiptChangeListener {

    private ReceiptChangeNotifierPosService notifierService;
    private CalculationPosService calculationPosService;
    private CDBSession dbSession;
    protected ReceiptManager receiptManager;

    @Override
    public String getId() {
        return "webhook";
    }

    @Override
    public String getName() {
        return "Webhook Addon";
    }

    @Override
    public String getVersion() {
        return "1.1.7";
    }

    @Override
    public boolean persistPropertiesToDB() {
        return true;
    }

    @Override
    public Map<String, String> getPluginPropertyConfig() {
        Map<String, String> propConfig = new HashMap<>();
        return propConfig;
    }

    @Override
    public void startup() {
        // this.dbSession = CDBSessionFactory.instance.createSession();
        this.dbSession=dbSession==null?CDBSessionFactory.instance.createSession():dbSession;
        this.notifierService = ServiceFactory.INSTANCE.getOrCreateServiceInstance(ReceiptChangeNotifierPosService.class,
                dbSession);
        this.calculationPosService = ServiceFactory.INSTANCE.getOrCreateServiceInstance(CalculationPosService.class,
                this.dbSession);
        notifierService.registerChangeListener(this);
        this.receiptManager = new ReceiptManager(this.dbSession);

        super.startup();
    }

    // this event when you add item into transaction
    public void onSalesItemAddedToReceipt(com.sap.scco.ap.pos.dao.CDBSession dbSession,
            com.sap.scco.ap.pos.entity.ReceiptEntity receipt,
            java.util.List<com.sap.scco.ap.pos.entity.SalesItemEntity> salesItems, java.math.BigDecimal quantity) {
        CalculateDiscount(dbSession,receipt);
    }

    // this event here when you updating item price, qty etc...
    public void onSalesItemUpdated(com.sap.scco.ap.pos.dao.CDBSession dbSession,
            com.sap.scco.ap.pos.entity.ReceiptEntity receipt, com.sap.scco.ap.pos.entity.SalesItemEntity newSalesItem,
            java.math.BigDecimal quantity) {


        CalculateDiscount(dbSession,receipt);
    }

    // public void onSalesItemVoided(com.sap.scco.ap.pos.dao.CDBSession dbSession,
    //         com.sap.scco.ap.pos.entity.ReceiptEntity receipt, com.sap.scco.ap.pos.entity.SalesItemEntity newSalesItem) {
    //     // CalculateDiscount(receipt);
    //     List<SalesItemEntity> salesItems = receipt.getSalesItems();
    //     for (SalesItemEntity sales : salesItems) {
    //         if (sales.getStatus().equals("1") && 
    //         (sales.getId().equals("075188")
    //         || sales.getId().equals("528082"))
    //         ) {
    //             sales.setPercentageDiscount(false);
                
    //             sales.setDiscountNetAmount(BigDecimal.valueOf(10.00));
    //             sales.setDiscountManuallyChanged(true);
    //             sales.setMarkChanged(true);
    //             sales.setItemDiscountChanged(true);
    //         }
    //         calculationPosService.calculate(receipt, EntityActions.CHECK_CONS);

    //         UIEventDispatcher.INSTANCE.dispatchAction(CConst.UIEventsIds.RECEIPT_REFRESH, null, receipt);

    //     }
    // }

    void setLineDiscount(SalesItemEntity salesItem,BigDecimal discount)
    {
        salesItem.setPercentageDiscount(false);
                            
                                // use this for your US tax System
                            // salesItem.setDiscountNetAmount(discount);
                                // this is for VAT
                            salesItem.setDiscountAmount(discount);

                            salesItem.setDiscountManuallyChanged(true);
                            salesItem.setMarkChanged(true);
                            salesItem.setItemDiscountChanged(true);
                            salesItem.setUnitPriceChanged(true);

    }
    void CalculateDiscount(com.sap.scco.ap.pos.dao.CDBSession dbsession ,com.sap.scco.ap.pos.entity.ReceiptEntity receipt) {
        org.slf4j.Logger logger = LoggerFactory.getLogger(DescountsAddon.class);
        logger.info("Calculating Discount...");

        try {
            // this.dbSession=dbsession;
            // Update the discount for each sales item
            List<SalesItemEntity> salesItems = receipt.getSalesItems();
            
            for (SalesItemEntity salesItem : salesItems) {
                

                if ((salesItem.getId().equals("074721") || salesItem.getId().equals("075188"))
                        || salesItem.getId().equals("528082")) {


                            

                    if (salesItem.getStatus().equals("1")) {

                        int qty = salesItem.getQuantity().intValue();

                        double discountNetAmount = 5.00; // BigDecimal.valueOf(9.99);
                        discountNetAmount = discountNetAmount * qty;

                        logger.info("Calculating Discount..." + discountNetAmount);

                        setLineDiscount(salesItem,BigDecimal.valueOf(discountNetAmount));
                        // salesItem.setPercentageDiscount(false);

                        // salesItem.setDiscountNetAmount(BigDecimal.valueOf(discountNetAmount).abs());
                        // salesItem.setDiscountNetAmount(BigDecimal.valueOf(10));
                        logger.info("Calculating Discount...ItemCode: " + salesItem.getId() + " Discount: "
                                + salesItem.getDiscountNetAmount());
                        
                                

                    } else {
                        BigDecimal discountNetAmount = BigDecimal.valueOf(0.00);
                        setLineDiscount(salesItem,discountNetAmount.abs());
                        // salesItem.setDiscountNetAmount(discountNetAmount.abs());
                        logger.info("Calculating Discount...No Discount" + discountNetAmount);

                    }

                    
                    // // true - avoid CCO to automatically calculate native discounts!
                    // salesItem.setDiscountManuallyChanged(true);
                    // salesItem.setMarkChanged(true);
                    // salesItem.setItemDiscountChanged(true);
                    // salesItem.setUnitPriceChanged(true);

                }

            }

            calculationPosService.calculate(receipt, EntityActions.CHECK_CONS);
            this.receiptManager = new ReceiptManager(this.dbSession);

            // calculationPosService.calculate(receipt, EntityActions.CHECK_CONS);
// 
            // UIEventDispatcher.INSTANCE.dispatchAction(CConst.UIEventsIds.RECEIPT_REFRESH, null, receipt);
        } catch (Exception e) {
            logger.error("Error occurred while calculating discount and refreshing receipt:", e);
        }
    }

}
