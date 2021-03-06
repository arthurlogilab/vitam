package fr.gouv.vitam.metadata.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.gouv.vitam.common.json.JsonHandler;

public class UnitInheritedRuleTest {
    
    private final static String EXPECTED_RESULT = "{\"inheritedRule\":" +
        "{\"StorageRule\":" +
            "{\"R1\":" +
                "{\"AU2\":{" +
                "\"FinalAction\":\"NoAccess\"," +
                "\"StartDate\":\"01/01/2017\"," +
                "\"path\":[[\"AU2\"]]}}}," +
        "\"AccessRule\":" +
            "{\"R2\":" +
                "{\"AU1\":{" +
                "\"FinalAction\":\"Access\"," +
                "\"StartDate\":\"01/01/2017\"," +
                "\"EndDate\":\"01/01/2019\"," +
                "\"path\":[[\"AU1\",\"AU2\"]]}}," +
            "\"R4\":{\"AU2\":{" +
                "\"FinalAction\":\"Access\"," +
                "\"StartDate\":\"01/01/2017\"," +
                "\"EndDate\":\"01/01/2019\"," +
                "\"path\":[[\"AU2\"]]}}}}}";
    
    private final static String EXPECTED_CONCAT_RESULT = "{\"inheritedRule\":" +
        "{\"AccessRule\":" +
            "{\"R2\":" +
                "{\"AU1\":{" +
                "\"FinalAction\":\"Access\"," +
                "\"StartDate\":\"01/01/2017\"," +
                "\"EndDate\":\"01/01/2019\"," +
                "\"path\":[[\"AU1\"]]}," +
                "\"AU3\":{" +
                "\"FinalAction\":\"Access\"," +
                "\"StartDate\":\"01/01/2017\"," +
                "\"EndDate\":\"01/01/2019\"," +
                "\"path\":[[\"AU3\"]]}}}}}";
    private final static String AU1_MGT = "{" +
        "    \"AccessRule\" : [{" +
        "      \"Rule\" : \"R2\"," +
        "      \"FinalAction\" : \"Access\"," +
        "      \"StartDate\" : \"01/01/2017\"," +
        "      \"EndDate\" : \"01/01/2019\"" +
        "    }], \"NeedAuthentication\" : false" +
        "  }";
    
    private final static String AU2_MGT = "{" +
        "    \"StorageRule\" : [{" +
        "      \"Rule\" : \"R1\"," +
        "      \"FinalAction\" : \"NoAccess\"," +
        "      \"StartDate\" : \"01/01/2017\"" +
        "    }]," +
        "    \"AccessRule\" : [{" +
        "      \"Rule\" : \"R4\"," +
        "      \"FinalAction\" : \"Access\"," +
        "      \"StartDate\" : \"01/01/2017\"," +
        "      \"EndDate\" : \"01/01/2019\"" +
        "    }]" +
        "  }";
    private final static String AU5_MGT = "{" +
        "    \"AccessRule\" : [{" +
        "      \"PreventInheritance\" : \"true\"" +
        "    }]" +
        "  }";
    
    @Test
    public void testUnitRuleResult() throws Exception {
        UnitInheritedRule au1RulesResult = new UnitInheritedRule((ObjectNode) JsonHandler.getFromString(AU1_MGT), "AU1");
        UnitInheritedRule au2RulesResult = au1RulesResult.createNewInheritedRule((ObjectNode) JsonHandler.getFromString(AU2_MGT), "AU2");
        UnitInheritedRule au3RulesResult = new UnitInheritedRule((ObjectNode) JsonHandler.getFromString(AU1_MGT), "AU3");
        assertEquals(EXPECTED_RESULT, JsonHandler.unprettyPrint(au2RulesResult));
        
        UnitInheritedRule au5RulesResult =
            new UnitInheritedRule((ObjectNode) JsonHandler.getFromString(AU5_MGT), "AU5");
        
        assertNotNull(au5RulesResult);
        au1RulesResult.concatRule(au3RulesResult);
        assertEquals(EXPECTED_CONCAT_RESULT, JsonHandler.unprettyPrint(au1RulesResult));
        
        au1RulesResult.concatRule(au1RulesResult);
        assertEquals(EXPECTED_CONCAT_RESULT, JsonHandler.unprettyPrint(au1RulesResult));
        
    }

}
