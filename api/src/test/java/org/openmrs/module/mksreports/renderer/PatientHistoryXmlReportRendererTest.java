package org.openmrs.module.mksreports.renderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Cohort;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Visit;
import org.openmrs.contrib.testdata.TestDataManager;
import org.openmrs.module.mksreports.dataset.definition.PatientHistoryEncounterAndObsDataSetDefinition;
import org.openmrs.module.reporting.common.SortCriteria;
import org.openmrs.module.reporting.data.patient.library.BuiltInPatientDataLibrary;
import org.openmrs.module.reporting.dataset.definition.PatientDataSetDefinition;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

public class PatientHistoryXmlReportRendererTest extends BaseModuleContextSensitiveTest {
	
	private static String OUTPUT_XML_OUTPUT_DIR = "target/test/";
	
	private static String OUTPUT_XML_OUTPUT_PATH = OUTPUT_XML_OUTPUT_DIR + "out_samplePatientHistory.xml";
	
	@Autowired
	private BuiltInPatientDataLibrary builtInPatientData;
	
	@Autowired
	private ReportDefinitionService reportDefinitionService;
	
	@Autowired
	private TestDataManager data;
	
	private ReportData reportData = null;
	
	private File file = null;
	
	private Patient p1 =null;
	
	@Before
	public void setUp() throws Exception {
		
		/*Load the xml file with test concepts, locations, patient identifier types, ...
		TODO This should be improved once we get a metadata management strategy.
		A good strategy would be for example to use metadatadeploy (...see https://wiki.openmrs.org/display/docs/Metadata+Deploy+Module)
		to bundle metadata within a module and perhaps have another module that depends on it that can provide metadata lookup utilities*/
		executeDataSet("org/openmrs/module/mksreports/include/ReportTestDataset.xml");
		
		PatientIdentifierType testIdentifierType = data.getPatientService().getPatientIdentifierType(4); //Social Security Number
		Location testLocation = data.getLocationService().getLocation(1); //Unknown Location
		
		//Build a test patient
		p1 = data.patient().name("Alice", "MKS Test").gender("F").birthdate("1975-01-02", false).dateCreated("2013-10-01").identifier(testIdentifierType, "Y2ATDN", testLocation).save();
			
		//Some concepts
		Concept wt = data.getConceptService().getConcept(5089);
		Concept civilStatus = data.getConceptService().getConcept(4);
		Concept single = data.getConceptService().getConcept(5);	
		Concept cd4Count = data.getConceptService().getConcept(5497);
		Concept dateOfFoodAssistance = data.getConceptService().getConcept(20);
		Concept favoriteFoodNonCoded = data.getConceptService().getConcept(19);
		Concept foodAssistance = data.getConceptService().getConcept(18);
		Concept foodAssistanceForEntireFamily = data.getConceptService().getConcept(21);
		Concept no = data.getConceptService().getConcept(8);
		Concept yes = data.getConceptService().getConcept(7);
		
		Visit v1 = data.visit().patient(p1).visitType(2).started("2005-01-01 00:00:00.0").stopped("2010-09-10 00:00:00.0").save();
		
		//Some encounters on visit v1
		data.randomEncounter().encounterDatetime("2007-08-01 00:00:00.0").encounterType(1).visit(v1).patient(p1).obs(wt, 51).obs(civilStatus, single).save();
		data.randomEncounter().encounterDatetime("2008-08-01 00:00:00.0").encounterType(6).visit(v1).patient(p1).obs(cd4Count, 150).obs(foodAssistanceForEntireFamily, no).obs(wt, 50).save();
		data.randomEncounter().encounterDatetime("2008-08-15 00:00:00.0").encounterType(2).visit(v1).patient(p1).obs(cd4Count, 175).obs(dateOfFoodAssistance, "2008-08-14 00:00:00.0").obs(favoriteFoodNonCoded, "PB and J").obs(foodAssistance,yes).obs(foodAssistanceForEntireFamily, yes).obs(wt, 55).save();
		data.randomEncounter().encounterDatetime("2009-09-19 00:00:00.0").encounterType(6).visit(v1).patient(p1).obs(wt, 61).save();
		
		Visit v2 = data.visit().patient(p1).visitType(3).started("2009-01-01 00:00:00.0").save();
		
		//Some encounters on visit v2
		data.randomEncounter().encounterDatetime("2009-08-19 00:00:00.0").encounterType(2).visit(v2).patient(p1).obs(cd4Count, 180).obs(foodAssistance, no).obs(wt, 80).save();
		data.randomEncounter().encounterDatetime("2009-09-19 00:00:00.0").encounterType(6).visit(v2).patient(p1).obs(cd4Count, 150).obs(foodAssistance, yes).obs(wt, 78).save();
		
		//Some encounters with no visit
		data.randomEncounter().encounterDatetime("2009-09-19 00:00:00.0").encounterType(1).patient(p1).obs(cd4Count, 49).obs(foodAssistance, yes).obs(wt, 180).save();
		data.randomEncounter().encounterDatetime("2010-09-26 00:00:00.0").encounterType(2).patient(p1).obs(cd4Count, 51).obs(foodAssistance, yes).obs(wt, 190).save();
		
		file = new File(OUTPUT_XML_OUTPUT_DIR);
		file.mkdirs();
	}
	
	/**
	 * Creates a new report definition and adds a PatientDataSetDefinition and
	 * PatientHistoryEncounterAndObsDataSetDefinition datasets to it
	 * 
	 * @return rd The new report definition
	 */
	protected ReportDefinition getReportDefinition() {
		ReportDefinition rd = new ReportDefinition();
		rd.setName("Testing Renderer");
		
		//Create a new dataset definition to hold the patient's demographics
		PatientDataSetDefinition demographicsDSD = new PatientDataSetDefinition();
		Map<String, Object> mappings = new HashMap<String, Object>();
		
		demographicsDSD.addColumn("ID", builtInPatientData.getPatientId(), mappings);
		demographicsDSD.addColumn("Given Name", builtInPatientData.getPreferredGivenName(), mappings);
		demographicsDSD.addColumn("Last Name", builtInPatientData.getPreferredFamilyName(), mappings);
		demographicsDSD.addColumn("Gender", builtInPatientData.getGender(), mappings);
		
		// Create a new dataset definition to hold the patient's encounters and obs
		PatientHistoryEncounterAndObsDataSetDefinition encountersDSD = new PatientHistoryEncounterAndObsDataSetDefinition();
		encountersDSD.setName("Patient History data set");
		encountersDSD.addSortCriteria("encounterDate", SortCriteria.SortDirection.ASC);
		
		//Attaching the tow datasets to the report definition
		rd.addDataSetDefinition("demographics", demographicsDSD, new HashMap<String, Object>());
		rd.addDataSetDefinition("encounters", encountersDSD, new HashMap<String, Object>());
		return rd;
	}
	
	/**
	 * Gets the report definition and builds an evaluation context with one patient (...the test patient p1)
	 * Creates then a report design that uses PatientHistoryExcelTemplateRenderer and renders the report to xml.
	 * @throws IOException
	 * @throws EvaluationException error evaluating the report definition
	 */
	@Test
	public void shoudProduceValidXml() throws IOException, EvaluationException {
		
		ReportDefinition reportDefinition  = getReportDefinition();
		// Populate a new EvaluationContext with the test patient
		EvaluationContext context = new EvaluationContext();
		Cohort baseCohort = new Cohort();
		baseCohort.addMember(p1.getPatientId());
		context.setBaseCohort(baseCohort);
		
		// Evaluate the report with this context to produce the data to use to populate the summary
		reportData = reportDefinitionService.evaluate(reportDefinition, context);
		
		final ReportDesign design = new ReportDesign();
		design.setName("TestDesign");
		design.setReportDefinition(reportDefinition);
		design.setRendererType(PatientHistoryXmlReportRenderer.class);
		
		PatientHistoryXmlReportRenderer renderer = new PatientHistoryXmlReportRenderer() {
			public ReportDesign getDesign(String argument) {
				return design;
			}
		};
		
		//Outputting the generated xml to target/test
		FileOutputStream fos = new FileOutputStream(OUTPUT_XML_OUTPUT_PATH);
		renderer.render(reportData, " ", fos);
		fos.close();
	}
}
