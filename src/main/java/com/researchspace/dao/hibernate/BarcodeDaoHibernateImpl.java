package com.researchspace.dao.hibernate;

import com.researchspace.dao.BarcodeDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.inventory.Barcode;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class BarcodeDaoHibernateImpl extends GenericDaoHibernate<Barcode, Long>
    implements BarcodeDao {

  public BarcodeDaoHibernateImpl(Class<Barcode> persistentClass) {
    super(persistentClass);
  }

  public BarcodeDaoHibernateImpl() {
    super(Barcode.class);
  }

  @Override
  public List<InventoryRecord> findItemsByBarcodeData(String barcodeData) {
    List<Barcode> matchingBarcodes =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from Barcode where barcodeData=:barcodeData and deleted=false", Barcode.class)
            .setParameter("barcodeData", barcodeData)
            .list();

    return matchingBarcodes.stream()
        .map(Barcode::getInventoryRecord)
        .distinct()
        .collect(Collectors.toList());
  }
}
