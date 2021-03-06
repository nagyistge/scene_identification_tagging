package it.emarolab.osr.scene.sceneLearning;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.ConvertSuperClassesToEquivalentClass;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectMinCardinality;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.SWRLIndividualArgument;
import org.semanticweb.owlapi.model.SWRLVariable;

import it.emarolab.amor.owlDebugger.Logger;
import it.emarolab.amor.owlInterface.OWLReferences;
import it.emarolab.osr.RecognitionManualSupervising;
import it.emarolab.osr.scene.sceneRecognition.PrimitiveRelation;
import it.emarolab.osr.scene.sceneRecognition.SceneIndividualCreator;


public class SceneLearner extends SWRLmanager{
	
	// TODO how to give always different and intelligent name while learning ?????????????????
	public static final String CLASS_NAME_LEARNED_BASE = "LearnedScene_"; 
	public static final String INDIVIDUAL_NAME_RECOGNITION_PARAMETER = "Parameter_Recognition";
	public static final String INDIVIDUAL_NAME_LEARNING_PARAMETER = "Parameter_Learning";
	public static final String PROPERTY_LEARNED_SCENE_ID = "hasParameter_LearningScene_cnt";
	public static final String PROPERTY_BASE_NAME_CARDINALITY = "hasParameter_SceneCardinality_";
	
	private String newSceneName;
	
	private static Long sceneId = 0l;
	
	private String logSWRL = " Add rule: ", logOWL = "";
	private Logger log;
	public static final Boolean DEBUG = RecognitionManualSupervising.DEBUG;
	
	public SceneLearner( List< PrimitiveRelation> sceneList, OWLReferences ontology){
		// initialize swrl manager
		super( ontology);
		
		this.log = new Logger( this, DEBUG);;

		newSceneName = createNewSceneName( ontology);
		
		// add new class for this learned scene as a sub class of "Scene"
		Integer classCardinality = 0;
		ontology.addSubClassOf( SceneIndividualCreator.CLASS_NAME_SCENE, newSceneName);
		logOWL += "learning new class: " +  newSceneName + " (as a subclass of " + SceneIndividualCreator.CLASS_NAME_SCENE + ")\n Add class Expressions: ";
		// create SWRL recognition rule
		SWRLIndividualArgument sceneIndividual = this.getConst( ontology.getOWLIndividual( SceneIndividualCreator.INDIVIDUAL_NAME_SCENE));
		for( PrimitiveRelation prRel : sceneList){
			// create the precondition part of the rule for this primitive relation in the scene
			createPrimitveRoleCondition( sceneIndividual, prRel, ontology);
			classCardinality += 2; // it consider a relation and its inverse
		}
		createPrimitiveRoleInferation( sceneIndividual, ontology);
		this.addRoole();
		addClassCardinalityReference( classCardinality, ontology);
		
		// add cardinality restriction in the class expression for scene hierarchical classification
		List< SemplifiedPrimitiveRelation> semplifiedSceneList = SemplifiedPrimitiveRelation.semplifyPrimitiveRelation( sceneList, newSceneName, ontology);
		addCardinalityRestriction( semplifiedSceneList, ontology);
		
		log.addDebugString( logOWL);
		log.addDebugString( logSWRL);
	}

	// get the value of hasLearningScene_idx to generate names for learnend entities
	// it updates also the state of the counter
	private String createNewSceneName( OWLReferences ontology){
		// get actual count value
		OWLNamedIndividual param = ontology.getOWLIndividual( INDIVIDUAL_NAME_LEARNING_PARAMETER);
		OWLDataProperty prop = ontology.getOWLDataProperty( PROPERTY_LEARNED_SCENE_ID);
		OWLLiteral value = ontology.getOnlyDataPropertyB2Individual(param, prop);
		OWLLiteral newValue;
		if( value == null){
			// create count
			sceneId = 0l;
			newValue = ontology.getOWLLiteral( sceneId);
		} else {
			// update count
			sceneId = Long.valueOf( value.getLiteral()) + 1l;
		    newValue = ontology.getOWLLiteral( sceneId);
			
		    // ontology.replaceDataProperty(param, prop, value, newValue, PrimitiveShapeData.BUFFERIZE_ONTOLOGY_CHANGES); 
		    // replace data property is not working ??? !!!! so rmove all the values first
		    Set< OWLLiteral> values = ontology.getDataPropertyB2Individual( param, prop);
		    for( OWLLiteral v : values){
		    	ontology.removeDataPropertyB2Individual(param, prop, v);
		    }
		}
		ontology.addDataPropertyB2Individual( param, prop, newValue);
		// generate name
		return( CLASS_NAME_LEARNED_BASE + sceneId);
	}
	
	// create preconditions in the role for every primitive relations
	private void createPrimitveRoleCondition( SWRLIndividualArgument sceneIndividual, PrimitiveRelation prRel, OWLReferences ontology) {
		// add scene conditions
		// add Ci+(?pi+)
		OWLClass preClass = prRel.getIndividualClasses( ontology).getPreClass();
		SWRLVariable preVar = this.getVariable( ontology.getOWLObjectName( prRel.getPreInd()));
		this.addClassCondition( preClass, preVar);
		logSWRL += OWLLog( preClass) + "( " + OWLLog( preVar) + "), ";
		// add Cj-(?pj-)
		OWLClass postClass = prRel.getIndividualClasses( ontology).getPostClass();
		SWRLVariable postVar = this.getVariable( ontology.getOWLObjectName( prRel.getPostInd()));
		this.addClassCondition( postClass, postVar);
		logSWRL += OWLLog( postClass) + "( " + OWLLog( postVar) + "), ";
		// add psi+(?si, ?pi+)
		OWLObjectProperty sceneProp = prRel.getRelation().getSceneProperty( ontology);
		this.addObjectPropertyCondition( sceneIndividual, sceneProp, preVar);
		logSWRL += OWLLog( sceneProp) + "( " + OWLLog( sceneIndividual) + ", " + OWLLog( preVar) + "), ";
		// add psi-(?si, ?pi-)
		this.addObjectPropertyCondition( sceneIndividual, sceneProp, postVar);
		logSWRL += OWLLog( sceneProp) + "( " + OWLLog( sceneIndividual) + ", " + OWLLog( postVar) + "), ";
		// add primitive conditions 
		OWLObjectProperty primitiveProp = prRel.getRelation().getProperty(ontology);
		this.addObjectPropertyCondition( preVar, primitiveProp, postVar);
		logSWRL += OWLLog( primitiveProp) + "( " + OWLLog( preVar) + ", " + OWLLog( postVar) + "), ";
	}
	
	// create assertion part of the rule
	private void createPrimitiveRoleInferation( SWRLIndividualArgument sceneIndividual, OWLReferences ontology){
		this.addClassInferation( ontology.getOWLClass( newSceneName), sceneIndividual);
		logSWRL += " -> " + newSceneName + "( " + OWLLog( sceneIndividual) + ") ";
	}
	
	// add LearnedScene = hasSceneComponent_* exactly * Primitive ...
	private void addCardinalityRestriction( List< SemplifiedPrimitiveRelation> semplifiedSceneList, OWLReferences ontology) {
		// set exactly relation as a sub class of relations
		for( SemplifiedPrimitiveRelation seRel : semplifiedSceneList){
			for( OWLClass value: seRel.getClassValues().keySet()){
			//for( ClassExactCardinality value : seRel.getClassValues()){
				OWLObjectMinCardinality cardinalityAxiom = ontology.getFactory().getOWLObjectMinCardinality( seRel.getClassValues().get(value), seRel.getProperty(), value);
				OWLSubClassOfAxiom subClussAxiom = ontology.getFactory().getOWLSubClassOfAxiom( seRel.getClassExpressionOf(), cardinalityAxiom);
				//arguments.add( subClussAxiom);
				ontology.getManager().addAxiom( ontology.getOntology(), subClussAxiom);
				logOWL +=  OWLLog( seRel.getClassExpressionOf()) + " " + OWLLog( seRel.getProperty()) + " min " + seRel.getClassValues().get(value) + " " + OWLLog( seRel.getProperty()) + " ^ ";
			}
		}
		// transform sub class of relations in class definition
		if( semplifiedSceneList.size() > 0){
			Set< OWLOntology> onts = new HashSet< OWLOntology>();
			onts.add( ontology.getOntology());
			List< OWLOntologyChange> changes = new ConvertSuperClassesToEquivalentClass( ontology.getFactory(), semplifiedSceneList.get(0).getClassExpressionOf(), onts, ontology.getOntology()).getChanges();
			ontology.applyOWLManipulatorChanges( changes);
		}
	}
	
	// add the cardinality to the learning parameter as a data property of type"hasScenCardinality_SceneName"
	private void addClassCardinalityReference(Integer classCardinality, OWLReferences ontology) {		
		OWLNamedIndividual paramInd = ontology.getOWLIndividual( INDIVIDUAL_NAME_LEARNING_PARAMETER);
		OWLDataProperty prop = ontology.getOWLDataProperty( PROPERTY_BASE_NAME_CARDINALITY + newSceneName);
		OWLLiteral value = ontology.getOWLLiteral( classCardinality);
		ontology.addDataPropertyB2Individual(paramInd, prop, value);
	}
	
	private static String OWLLog( OWLObject o){
		return OWLReferences.getOWLName( o);
	}
	
	public String getSceneName(){
		return this.newSceneName;
	}
}

