/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package metcap;
import java.math.BigDecimal;

import org.apache.fop.pdf.StandardStructureTypes.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sap.scco.ap.plugin.BasePlugin;
import com.sap.scco.ap.pos.dao.CDBSession;
import com.sap.scco.ap.pos.dao.CDBSessionFactory;
import com.sap.scco.ap.pos.entity.BaseEntity.EntityActions;
import com.sap.scco.ap.pos.entity.SalesItemEntity;
import com.sap.scco.ap.pos.service.ServiceFactory;
import com.sap.scco.ap.pos.service.CalculationPosService;
import com.sap.scco.ap.pos.service.ReceiptChangeNotifierPosService;
import com.sap.scco.ap.pos.service.component.listener.ReceiptChangeListener;
import com.sap.scco.env.UIEventDispatcher;
import com.sap.scco.util.CConst;



public class DescountsAddon extends BasePlugin implements ReceiptChangeListener {
    private static Logger logger = LoggerFactory.getLogger(DescountsAddon.class);
    private ReceiptChangeNotifierPosService notifierService;
    private CalculationPosService calculationPosService;
    private CDBSession dbSession;

    @Override
    public String getId() {
        return "MetCapCoupons";
    }

    @Override
    public String getName() {
        return "MetCap Coupons Promo";
    }
    

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void startup() {
        this.dbSession = CDBSessionFactory.instance.createSession();
        this.notifierService =ServiceFactory.INSTANCE.getOrCreateServiceInstance(ReceiptChangeNotifierPosService.class,dbSession);
        this.calculationPosService = ServiceFactory.INSTANCE.getOrCreateServiceInstance(CalculationPosService.class,dbSession);
        notifierService.registerChangeListener(this);
        
    }

    void ApplyDiscount(com.sap.scco.ap.pos.entity.ReceiptEntity receipt)
    {
        //This function clear tax items for the sales item
          try {
                                                // Update the discount for each sales item
                                                List<SalesItemEntity> salesItems = receipt.getSalesItems();
                                                for (SalesItemEntity salesItem : salesItems) {
                                                                salesItem.setDiscountNetAmount(new BigDecimal(2.00));
                                                                salesItem.setPaymentNetAmount(new BigDecimal(19.99));
                                                                
                                                                salesItem.setDiscountManuallyChanged(true);

                                                                // true - avoid  CCO to automatically calculate native discounts!
                                                                salesItem.setDiscountManuallyChanged(true);
                                                                salesItem.setMarkChanged(true);
                                                                salesItem.setItemDiscountChanged(true);


                                                                // salesItem.setItemDiscountChanged(true);
                                                                // salesItem.setItemDiscountAlreadyFetched(false);
                                                                // salesItem.setPercentageDiscount(false);
                                                                // salesItem.setDiscountAmount(new BigDecimal(2.00));
                                                                // salesItem.setDiscountManuallyChanged(false);
                                                                // salesItem.setMarkChanged(true);
                                                }
 
                                                // Use calculationPosService to recalculate transaction
                                                calculationPosService.calculate(receipt, EntityActions.CHECK_CONS);

                                                UIEventDispatcher.INSTANCE.dispatchAction(CConst.UIEventsIds.RECEIPT_REFRESH, null, receipt);
 
                                                logger.info("Discount calculation and receipt refresh successful.");
 
                                } catch (Exception e) {
                                                logger.error("Error occurred while calculating discount and refreshing receipt:", e);
            
                                            }
        
    }

    //this event when you add item into transaction
    public  void onSalesItemAddedToReceipt(com.sap.scco.ap.pos.dao.CDBSession dbSession, com.sap.scco.ap.pos.entity.ReceiptEntity receipt, java.util.List<com.sap.scco.ap.pos.entity.SalesItemEntity> salesItems, java.math.BigDecimal quantity) {
        ApplyDiscount(receipt);
    }
    //this event here when you updating ite price, qty etc...
    public void onSalesItemUpdated(com.sap.scco.ap.pos.dao.CDBSession dbSession, com.sap.scco.ap.pos.entity.ReceiptEntity receipt, com.sap.scco.ap.pos.entity.SalesItemEntity newSalesItem, java.math.BigDecimal quantity) {
        ApplyDiscount(receipt);

    }



    

    public void onReceiptPost(com.sap.scco.ap.pos.dao.CDBSession dbSession, com.sap.scco.ap.pos.entity.ReceiptEntity receipt) {
        
        logger.info("POST-RECEIPT EVENT");
        var cpns=receipt.getCouponAssignments();
        if(cpns.size()>0)
        {
            //Get First Coupon
            var coupon=cpns.get(0).getCoupon();
            //Print Coupon Code
            logger.info(coupon.getCode());
            //Print Coupon Key
            logger.info(coupon.getKey());
            
            
        }
        logger.info(String.valueOf(cpns.size()));
        receipt.getPaymentItems().forEach(a ->{
            logger.info(a.getDescription());
            
        });;
        
        // new SalesController(this,dbSession).postReceipt(receipt);
    }

    
}
