package com.dotmarketing.portlets.rules.conditionlet;

import com.dotcms.repackage.com.google.common.collect.Lists;
import com.dotcms.repackage.com.google.common.collect.Maps;
import com.dotcms.repackage.com.maxmind.geoip2.exception.GeoIp2Exception;
import com.dotcms.unittest.TestUtil;
import com.dotmarketing.business.web.LanguageWebAPI;
import com.dotmarketing.portlets.languagesmanager.model.Language;
import com.dotmarketing.portlets.rules.exception.ComparisonNotSupportedException;
import com.dotmarketing.portlets.rules.model.ParameterModel;
import com.dotmarketing.portlets.rules.parameter.comparison.Comparison;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.dotmarketing.portlets.rules.conditionlet.Conditionlet.COMPARISON_KEY;
import static com.dotmarketing.portlets.rules.conditionlet.VisitorsLanguageConditionlet.LANGUAGE_KEY;
import static com.dotmarketing.portlets.rules.parameter.comparison.Comparison.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UsersReferrerURLConditionletTest {

    private static final String MOCK_IP_ADDRESS = "10.0.0.1";

    @BeforeMethod
    public void setUp() throws Exception {

    }

    @DataProvider(name = "cases")
    public Object[][] compareCases() throws Exception {
        try {
            List<TestCase> data = Lists.newArrayList();


        /* Is */
            data.add(new TestCase("If GB set and visitor's location is GB , evaluate to true.")
                         .withComparison(IS)
                         .withReferrer("GB")
                         .withMockReferrer("GB")
                         .shouldBeTrue()
            );

            data.add(new TestCase("Ignores case - If gb set and visitor's location is GB , evaluate to true.")
                         .withComparison(IS)
                         .withReferrer("gb")
                         .withMockReferrer("GB")
                         .shouldBeTrue()
            );

            data.add(new TestCase("Ignores case - If GB set and visitor's location is gb , evaluate to true.")
                         .withComparison(IS)
                         .withReferrer("GB")
                         .withMockReferrer("gb")
                         .shouldBeTrue()
            );

            data.add(new TestCase("If GB set and visitor's location is US , evaluate to false.")
                         .withComparison(IS)
                         .withReferrer("GB")
                         .withMockReferrer("US")
                         .shouldBeFalse()
            );

            /* Is Not*/
            data.add(new TestCase("Is Not: If GB set and visitor's location is US , evaluate to true.")
                         .withComparison(IS_NOT)
                         .withReferrer("GB")
                         .withMockReferrer("US")
                         .shouldBeTrue()
            );

            data.add(new TestCase("Is Not: If GB set and visitor's location is GB , evaluate to false.")
                         .withComparison(IS_NOT)
                         .withReferrer("GB")
                         .withMockReferrer("GB")
                         .shouldBeFalse()
            );


            return TestUtil.toCaseArray(data);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test(dataProvider = "cases")
    public void testComparisons(TestCase aCase) throws Exception {
        assertThat(aCase.testDescription, runCase(aCase), is(aCase.expect));
    }

    private boolean runCase(TestCase aCase) {
        return aCase.conditionlet.evaluate(aCase.request, aCase.response, aCase.conditionlet.instanceFrom(aCase.params));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testEvaluatesToFalseWhenArgumentsAreEmptyOrMissing() throws Exception {
        new TestCase("").conditionlet.instanceFrom(null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testCannotValidateWhenComparisonIsNull() throws Exception {
        TestCase aCase = new TestCase("Empty parameter list should throw NPE.").withComparison(null);
        new TestCase("").conditionlet.instanceFrom(aCase.params);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testCannotValidateWhenComparisonNotSet() throws Exception {
        new TestCase("").conditionlet.instanceFrom(Maps.newHashMap());
    }


    @Test(expectedExceptions = ComparisonNotSupportedException.class)
    public void testUnsupportedComparisonThrowsException() throws Exception {
        TestCase aCase = new TestCase("Exists: Unsupported comparison should throw.")
            .withComparison(EXISTS)
            .withReferrer("GB")
            .withMockReferrer("GB")
            .shouldBeFalse();
        runCase(aCase);
    }

    private class TestCase {

        public final UsersReferringURLConditionlet conditionlet;
        public final LanguageWebAPI langApi = mock(LanguageWebAPI.class);
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final Map<String, ParameterModel> params = Maps.newLinkedHashMap();
        private final String testDescription;

        private boolean expect;

        public TestCase(String testDescription) {
            this.testDescription = testDescription;
            this.request = mock(HttpServletRequest.class);
            this.response = mock(HttpServletResponse.class);
            conditionlet = new UsersReferringURLConditionlet(langApi);
        }

        TestCase shouldBeTrue() {
            this.expect = true;
            return this;
        }

        TestCase shouldBeFalse() {
            this.expect = false;
            return this;
        }

        TestCase withComparison(Comparison c) {
            params.put(COMPARISON_KEY, new ParameterModel(COMPARISON_KEY, c != null ? c.getId() : null));
            return this;
        }

        TestCase withMockReferrer(String mockReferrer) throws IOException, GeoIp2Exception {
            Language mockLang = mock(Language.class);
            when(mockLang.getLanguageCode()).thenReturn(mockReferrer);
            when(langApi.getLanguage(request)).thenReturn(mockLang);
            return this;
        }

        TestCase withReferrer(String referrer) {
            params.put(LANGUAGE_KEY, new ParameterModel(LANGUAGE_KEY, referrer));
            return this;
        }

        @Override
        public String toString() {
            return testDescription;
        }
    }
}
