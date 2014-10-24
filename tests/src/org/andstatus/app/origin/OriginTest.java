package org.andstatus.app.origin;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.MbConfig;
import org.andstatus.app.net.OAuthClientKeysTest;
import org.andstatus.app.origin.Origin.Builder;
import org.andstatus.app.util.UrlUtils;

public class OriginTest  extends InstrumentationTestCase {

        @Override
        protected void setUp() throws Exception {
            super.setUp();
            TestSuite.initialize(this);
        }

        public void testTextLimit() {
            String urlString = "https://github.com/andstatus/andstatus/issues/41";
            String message = "I set \"Shorten URL with: QTTR_AT\" URL longer than 25 Text longer than 140. Will this be shortened: " + urlString;

            Origin origin = MyContextHolder.get().persistentOrigins().firstOfType(OriginType.ORIGIN_TYPE_DEFAULT);
            assertEquals(origin.originType, OriginType.TWITTER); 

            origin = MyContextHolder.get().persistentOrigins().firstOfType(OriginType.TWITTER);
            assertEquals(origin.originType, OriginType.TWITTER); 
            int textLimit = 140;
            assertEquals("Textlimit", textLimit, origin.getTextLimit());
            assertEquals("Short URL length", 23, origin.shortUrlLength);
            int charactersLeft = origin.charactersLeftForMessage(message);
            // Depending on number of spans!
            assertTrue("Characters left " + charactersLeft, charactersLeft == 18 || charactersLeft == 2);

            origin = MyContextHolder.get().persistentOrigins().firstOfType(OriginType.PUMPIO);
            textLimit = 5000;
            assertEquals("Textlimit", textLimit, origin.getTextLimit());
            assertEquals("Short URL length", 0, origin.shortUrlLength);
            assertEquals("Characters left", origin.getTextLimit() - message.length(), origin.charactersLeftForMessage(message));
            
            origin = MyContextHolder.get().persistentOrigins().firstOfType(OriginType.STATUSNET);
            textLimit = 200;
            int uploadLimit = 0;
            MbConfig config = MbConfig.fromTextLimit(textLimit, uploadLimit);
            origin = new Origin.Builder(origin).save(config).build();
            assertEquals("Textlimit", textLimit, origin.getTextLimit());
            
			textLimit = 140;
            config = MbConfig.fromTextLimit(textLimit, uploadLimit);
            origin = new Origin.Builder(origin).save(config).build();
            assertEquals("Textlimit", textLimit, origin.getTextLimit());
            assertEquals("Short URL length", 0, origin.shortUrlLength);
            assertEquals("Characters left", textLimit - message.length(), origin.charactersLeftForMessage(message));
			
            textLimit = 0;
            config = MbConfig.fromTextLimit(textLimit, uploadLimit);
            assertFalse(config.isEmpty());
			config.shortUrlLength = 24;
            origin = new Origin.Builder(origin).save(config).build();
            assertEquals("Textlimit", OriginType.TEXT_LIMIT_MAXIMUM, origin.getTextLimit());
            assertEquals("Short URL length", config.shortUrlLength, origin.shortUrlLength);
            assertEquals("Characters left", origin.getTextLimit() - message.length()
                    - config.shortUrlLength + urlString.length()
                    , origin.charactersLeftForMessage(message));
         }
        
        public void testAddDeleteOrigin() {
            String seed = Long.toString(System.nanoTime());
            String originName = "snTest" + seed;
            OriginType originType = OriginType.STATUSNET;
            String hostOrUrl = "sn" + seed + ".example.org";
            boolean isSsl = true;
            boolean allowHtml = true;
            createOneOrigin(originType, originName, hostOrUrl, isSsl, allowHtml);
            createOneOrigin(originType, originName, "https://" + hostOrUrl + "/somepath", isSsl, allowHtml);
            createOneOrigin(originType, originName, "https://" + hostOrUrl + "/pathwithslash/", isSsl, allowHtml);
            isSsl = false;
            Origin.Builder builder = createOneOrigin(originType, originName, hostOrUrl, isSsl, allowHtml);
            Origin origin = builder.build();
            assertEquals("New origin has no children", false, origin.hasChildren());
            assertEquals("Origin deleted", true, builder.delete());
        }
        
        public static Builder createOneOrigin(OriginType originType, String originName, String hostOrUrl,
                boolean isSsl, boolean allowHtml) {
            Origin.Builder builder = new Origin.Builder(originType);
            builder.setName(originName);
            builder.setHostOrUrl(hostOrUrl);
            builder.setSsl(isSsl);
            builder.setHtmlContentAllowed(allowHtml);
            builder.save();
            Origin origin = builder.build();
            if (origin.isOAuthDefault() || origin.canChangeOAuth()) {
                OAuthClientKeysTest.insertTestKeys(origin);
            }
            
            checkAttributes(originName, hostOrUrl, isSsl, allowHtml, origin);
            
            MyContextHolder.get().persistentOrigins().initialize();
            Origin origin2 = MyContextHolder.get().persistentOrigins().fromId(origin.getId());
            checkAttributes(originName, hostOrUrl, isSsl, allowHtml, origin2);
            
            return builder;
        }

        private static void checkAttributes(String originName, String hostOrUrl, boolean isSsl,
                boolean allowHtml, Origin origin) {
            assertTrue("Origin " + originName + " added", origin.isPersistent());
            assertEquals(originName, origin.getName());
            if (origin.canSetUrlOfOrigin()) {
                if (UrlUtils.isHostOnly(UrlUtils.buildUrl(hostOrUrl, isSsl))) {
                    assertEquals((isSsl ? "https" : "http") + "://" + hostOrUrl, origin.getUrl().toExternalForm());
                } else {
                    String hostOrUrl2 = hostOrUrl.endsWith("/") ? hostOrUrl : hostOrUrl + "/";
                    assertEquals("Input host or URL: '" + hostOrUrl + "'", UrlUtils.buildUrl(hostOrUrl2, origin.isSsl()), origin.getUrl());
                }
            } else {
                assertEquals(origin.getOriginType().urlDefault, origin.getUrl());
            }
            assertEquals(isSsl, origin.isSsl());
            assertEquals(allowHtml, origin.isHtmlContentAllowed());
        }
}
