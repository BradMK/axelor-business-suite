package com.axelor.apps.hr.service.batch;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PeriodRepository;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.hr.db.EmployeeBonusMgtLine;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.HrBatch;
import com.axelor.apps.hr.db.PayrollLeave;
import com.axelor.apps.hr.db.PayrollPreparation;
import com.axelor.apps.hr.db.repo.HrBatchRepository;
import com.axelor.apps.hr.db.repo.PayrollLeaveRepository;
import com.axelor.apps.hr.db.repo.PayrollPreparationRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.PayrollPreparationService;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.tool.file.CsvTool;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaFileRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;



public class BatchPayrollPreparationExport extends BatchStrategy {
	
	private final Logger log = LoggerFactory.getLogger( getClass() );
	
	private int total;
	private HrBatch hrBatch;
	private Company company;
	
	protected PayrollPreparationService payrollPreparationService;
	
	@Inject
	PayrollPreparationRepository payrollPreparationRepository;
	
	@Inject
	CompanyRepository companyRepository;
	
	@Inject
	PeriodRepository periodRepository;
	
	@Inject
	HRConfigService hrConfigService;
	
	@Inject
	public BatchPayrollPreparationExport(PayrollPreparationService payrollPreparationService) {
		super();
		this.payrollPreparationService = payrollPreparationService;
	}
	
	@Override
	protected void start() throws IllegalArgumentException, IllegalAccessException, AxelorException {
		
		super.start();
		
		total = 0;
		hrBatch = Beans.get(HrBatchRepository.class).find(batch.getHrBatch().getId());
		
		checkPoint();
	}

	
	@Override
	protected void process() {
		
		String exportAll = "";
		if (!hrBatch.getExportAlreadyExported()) { exportAll = " AND self.exported = false "; }
		
		List<PayrollPreparation> payrollPreparationList = payrollPreparationRepository.all().filter("self.company = ?1 AND self.period = ?2 " + exportAll, hrBatch.getCompany(), hrBatch.getPeriod() ).fetch();
		
		switch (hrBatch.getPayrollPreparationExportTypeSelect()) {
			case HrBatchRepository.EXPORT_TYPE_STANDARD:
				try {
					batch.setMetaFile( standardExport(payrollPreparationList) );
				} catch (IOException e) {
					incrementAnomaly();
					TraceBackService.trace(e, IException.LEAVE_MANAGEMENT, batch.getId());
				}
				break;
			case HrBatchRepository.EXPORT_TYPE_MEILLEURE_GESTION:
				try {
					batch.setMetaFile( meilleureGestionExport(payrollPreparationList) );
				} catch (Exception e) {
					incrementAnomaly();
					TraceBackService.trace(e, IException.LEAVE_MANAGEMENT, batch.getId());
				}
				break;
			default:
				break;
		}
	}
	
	@Transactional
	public MetaFile standardExport(List<PayrollPreparation> payrollPreparationList) throws IOException {
		
		List<String[]> list = new ArrayList<String[]>();
		LocalDate today = Beans.get(GeneralService.class).getTodayDate();
		
		for (PayrollPreparation payrollPreparation : payrollPreparationList) {
			String item[] = new String[5];
			item[0] = payrollPreparation.getEmployee().getName();
			item[1] = payrollPreparation.getDuration().toString();
			item[2] = payrollPreparation.getLunchVoucherNumber().toString();
			item[3] = payrollPreparation.getEmployeeBonusAmount().toString();
			item[4] = payrollPreparation.getExtraHoursNumber().toString();
			list.add(item);
			
			payrollPreparation.setExported(true);
			payrollPreparation.setExportDate(today);
			payrollPreparation.setExportTypeSelect(HrBatchRepository.EXPORT_TYPE_STANDARD);
			payrollPreparation.addBatchListItem(batch);
			payrollPreparationRepository.save(payrollPreparation);
			total ++;
			incrementDone();
			
		}
		
		String fileName = Beans.get(PayrollPreparationService.class).getPayrollPreparationExportName();
		String filePath = AppSettings.get().get("file.upload.dir");
		
		MetaFile metaFile = new MetaFile();
		metaFile.setFileName(fileName);
		metaFile.setFilePath(fileName);
		metaFile = Beans.get(MetaFileRepository.class).save(metaFile);
		
		new File(filePath).mkdirs();
		CsvTool.csvWriter(filePath, fileName, ';', Beans.get(PayrollPreparationService.class).getPayrollPreparationExportHeader(), list);
		
		
		return metaFile;
		
	}
	
	
	@Transactional
	public MetaFile meilleureGestionExport(List<PayrollPreparation> payrollPreparationList)  throws IOException, AxelorException {
		
		List<String[]> list = new ArrayList<String[]>();
		
		for (PayrollPreparation payrollPreparation : payrollPreparationList) {
			
			payrollPreparation.addBatchListItem(batch);
			payrollPreparationService.exportMeilleureGestion(payrollPreparation, list);
			total ++;
		}
		
		String fileName = Beans.get(PayrollPreparationService.class).getPayrollPreparationExportName();
		String filePath = AppSettings.get().get("file.upload.dir");
		
		MetaFile metaFile = new MetaFile();
		metaFile.setFileName(fileName);
		metaFile.setFilePath(fileName);
		metaFile = Beans.get(MetaFileRepository.class).save(metaFile);
		
		new File(filePath).mkdirs();
		CsvTool.csvWriter(filePath, fileName, ';', Beans.get(PayrollPreparationService.class).getPayrollPreparationMeilleurGestionExportHeader(), list);
		
		return metaFile;
	}
	
	
	
	@Override
	protected void stop() {
		
		String comment = String.format(I18n.get(IExceptionMessage.BATCH_PAYROLL_PREPARATION_EXPORT_RECAP) + '\n', total); 
		
		addComment(comment);
		super.stop();
	}

}