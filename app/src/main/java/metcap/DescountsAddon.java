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

import com.sap.scco.ap.configuration.ConfigurationHelper;
import com.sap.scco.ap.plugin.BasePlugin;
import com.sap.scco.ap.plugin.annotation.PluginAt;
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
import com.sap.scco.ap.pos.entity.TaxationMethod;
import com.sap.scco.ap.pos.entity.UserEntity;
import com.sap.scco.ap.pos.entity.coupon.AccountCouponEntity;
import com.sap.scco.ap.pos.entity.coupon.CouponAssignmentEntity;
import com.sap.scco.ap.pos.service.CalculationPosService;
//import com.sap.scco.ap.pos.entity.coupon.CouponAssignmentEntity;
import com.sap.scco.ap.pos.service.ReceiptChangeNotifierPosService;
import com.sap.scco.ap.pos.service.ReceiptPosService;
import com.sap.scco.ap.pos.service.SalesItemNotePosService;
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
    private  boolean isUSTaxSystem=false;

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
        isUSTaxSystem=ConfigurationHelper.INSTANCE.getCashDesk().getTaxSettings().getTaxationMethod().getCode()==TaxationMethod.TAX_JURISDICTION_BASED.getCode();

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

    public void onSalesItemVoided(com.sap.scco.ap.pos.dao.CDBSession dbSession,
            com.sap.scco.ap.pos.entity.ReceiptEntity receipt, com.sap.scco.ap.pos.entity.SalesItemEntity salesItem) {

                CalculateDiscount(dbSession,receipt);
                this.receiptManager = new ReceiptManager(this.dbSession);
            receiptManager.update(receipt);


                // salesItem.setNotes(null);
                
                // salesItem.setPercentageDiscount(false);


                // // SetLineDiscount(salesItem, BigDecimal.ZERO);
                // salesItem.setDiscountAmount(BigDecimal.ZERO);


                // salesItem.setMarkChanged(true);
                // salesItem.setItemDiscountChanged(true);

                // salesItem.setDiscountManuallyChanged(false);


        
                // // Calculate(receipt);
                // CalculateDiscount(dbSession,receipt);
                
                // var calculationPosService = ServiceFactory.INSTANCE.getOrCreateServiceInstance(CalculationPosService.class,this.dbSession);
                //     calculationPosService.calculate(receipt, EntityActions.CHECK_CONS);
                // receiptManager.update(receipt);





        
    }

    void setLineDiscount(SalesItemEntity salesItem,BigDecimal discount)
    {
        salesItem.setPercentageDiscount(false);
                            
                                // use this for your US tax System
                                if(isUSTaxSystem)
                                    salesItem.setDiscountNetAmount(discount);
                                // this is for VAT
                                else
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
            var hasDeleted=salesItems.stream().anyMatch(a->a.getStatus()!="1");
            
            for (SalesItemEntity salesItem : salesItems) {
                // if (salesItem.getStatus().equals("1")) 
                {
                

                if ((salesItem.getId().equals("074721") || salesItem.getId().equals("075188"))
                        || salesItem.getId().equals("528082")) {

                    
                            

                    

                        int qty = salesItem.getQuantity().intValue();

                        double discountNetAmount = //salesItems.stream().filter(a->a.getStatus()=="1").count();
                        hasDeleted?3.00:5.00; // BigDecimal.valueOf(9.99);
                        discountNetAmount = discountNetAmount * qty;

                        logger.info("Calculating Discount..." + discountNetAmount);

                        setLineDiscount(salesItem,BigDecimal.valueOf(discountNetAmount));
                        // salesItem.setPercentageDiscount(false);

                        // salesItem.setDiscountNetAmount(BigDecimal.valueOf(discountNetAmount).abs());
                        // salesItem.setDiscountNetAmount(BigDecimal.valueOf(10));
                        logger.info("Calculating Discount...ItemCode: " + salesItem.getId() + " Discount: "
                                + salesItem.getDiscountNetAmount());
                        
                                

                    } 
                    // else {
                    //     BigDecimal discountNetAmount = BigDecimal.valueOf(0.00);
                    //     setLineDiscount(salesItem,discountNetAmount.abs());
                    //     // salesItem.setDiscountNetAmount(discountNetAmount.abs());
                    //     logger.info("Calculating Discount...No Discount" + discountNetAmount);

                    // }

                    
                    // // true - avoid CCO to automatically calculate native discounts!
                    // salesItem.setDiscountManuallyChanged(true);
                    // salesItem.setMarkChanged(true);
                    // salesItem.setItemDiscountChanged(true);
                    // salesItem.setUnitPriceChanged(true);

                }

            }

            calculationPosService.calculate(receipt, EntityActions.CHECK_CONS);
            this.receiptManager = new ReceiptManager(this.dbSession);
            // receiptManager.update(receipt);

            // calculationPosService.calculate(receipt, EntityActions.CHECK_CONS);
// 
            // UIEventDispatcher.INSTANCE.dispatchAction(CConst.UIEventsIds.RECEIPT_REFRESH, null, receipt);
        } catch (Exception e) {
            logger.error("Error occurred while calculating discount and refreshing receipt:", e);
        }
    }

    // @PluginAt(pluginClass = SalesItemNotePosService.class, method = "removeSalesItemNote", where = PluginAt.POSITION.AFTER)
    // public Object removeSalesItemNote(Object proxy, Object[] args, Object ret, StackTraceElement caller)
    // {
    //     ReceiptEntity receipt=(ReceiptEntity)args[0];
    //     CalculateDiscount(this.dbSession,receipt);
    //     return  null;
    // }

    // @PluginAt(pluginClass = ReceiptPosService.class, method = "updateSalesItem", where = PluginAt.POSITION.BEFORE)
    // public void updateSalesItemBefore(Object proxy, Object[] args, Object ret)
    // {
    //     if(args.length>3)
    //     {
    //         CalculateDiscount(this.dbSession,(ReceiptEntity)args[0]);
    //         // SalesController controller=new SalesController(this,((ReceiptPosService)proxy).getDbSession());
    //         // HashSet hashset=(HashSet)args[3];
    //         // if(hashset.contains("discountAmount") || hashset.contains("discountPercentage"))
    //         // if(AvailableForPromo((ReceiptEntity)args[0]))
    //         //     controller.onManuallyUpdateItemDiscount((ReceiptEntity)args[0],(SalesItemEntity)args[1]);
     
    //         }
    // }

}
