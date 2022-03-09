package life.genny.adi.live.data;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;

import org.drools.core.common.InternalAgenda;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.kogito.legacy.rules.KieRuntimeBuilder;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.reactive.messaging.annotations.Blocking;
import life.genny.qwandaq.Answer;
import life.genny.qwandaq.Answers;
import life.genny.qwandaq.message.QDataAnswerMessage;
import life.genny.qwandaq.models.GennyToken;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.serviceq.Service;

@ApplicationScoped
public class InternalConsumer {

	static final Logger log = Logger.getLogger(InternalConsumer.class);

    static Jsonb jsonb = JsonbBuilder.create();

    @Inject
    KieRuntimeBuilder ruleRuntime;

	@Inject
	Service service;

	KieSession ksession;

	/**
	* Execute on start up.
	*
	* @param ev
	 */
    void onStart(@Observes StartupEvent ev) {

		service.fullServiceInit();
		log.info("[*] Finished Startup!");
    }

	/**
	* Consume from the valid_data topic.
	*
	* @param data
	 */
    @Incoming("valid_data")
    @Blocking
    public void getValidData(String data) {

        log.infov("Incoming Valid Data : {}", data);
        Instant start = Instant.now();

		BaseEntityUtils beUtils = service.getBeUtils();
		GennyToken serviceToken = beUtils.getServiceToken();
		GennyToken userToken = null;

        // deserialise to msg
        QDataAnswerMessage msg = jsonb.fromJson(data, QDataAnswerMessage.class);

        // check the token
        String token = msg.getToken();
        try {
            userToken = new GennyToken(token);
        } catch (Exception e) {
            log.error("Invalid Token!");
            return;
        }

        // update the token of our utility
        beUtils.setGennyToken(userToken);

		Answer answer = msg.getItems()[0];
		Answers answersToSave = new Answers();

        // init session and activate DataProcessing
        KieSession ksession = ruleRuntime.newKieSession();
        ((InternalAgenda) ksession.getAgenda()).activateRuleFlowGroup("DataProcessing");

        // insert facts into session
		ksession.insert(beUtils);
        ksession.insert(serviceToken);
        ksession.insert(userToken);
		ksession.insert(answer);
		ksession.insert(answersToSave);

        // fire rules and dispose of session
        ksession.fireAllRules();
        ksession.dispose();

		// TODO: ensure that answersToSave has been updated by our rules
		beUtils.saveAnswers(answersToSave);

        Instant end = Instant.now();
        log.info("Duration = " + Duration.between(start, end).toMillis() + "ms");
    }

}
