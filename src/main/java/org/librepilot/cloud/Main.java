package org.librepilot.cloud; /**
 * Created by marc on 19.08.2016.
 */

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.librepilot.cloud.handler.ConvertHandler;
import org.librepilot.cloud.handler.UAVODatabaseUpdateHandler;

public class Main {

    private static Server server;

    public static void main(String[] args ) throws Exception {

        if(args[0] != null && args[1] != null && args[0].equals("jetty")) {
            //as first arg, the heroku assigned port is given to the main method
            server = new Server(Integer.parseInt(args[1]));
            server.setStopAtShutdown(true);
            server.setStopTimeout(3000);

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        Log.i("SIG", "Gracefully stopping Webserver");
                        server.stop();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            HashSessionIdManager idmanager = new HashSessionIdManager();
            server.setSessionIdManager(idmanager);

            HashSessionManager manager = new HashSessionManager();
            SessionHandler sessions = new SessionHandler(manager);

            String basePath = Main.class.getClassLoader().getResource("web/static").toExternalForm();
            ResourceHandler resourceHandler = new ResourceHandler();
            resourceHandler.setResourceBase(basePath);
            resourceHandler.setDirectoriesListed(true);
            ContextHandler context = new ContextHandler("/");
            context.setHandler(resourceHandler);
/*
        ContextHandler contextSettings = new ContextHandler("/settings");
        contextSettings.setHandler(new ObjectHandler(dev, true, false));

        ContextHandler contextState = new ContextHandler("/state");
        contextState.setHandler(new ObjectHandler(dev, false, true));

        ContextHandler contextObjects = new ContextHandler("/objects");
        contextObjects.setAllowNullPathInfo(true);
        contextObjects.setHandler(new ObjectHandler(dev, true, true));

        ContextHandler contextDefSettings = new ContextHandler("/defsettings");
        contextDefSettings.setHandler(new DefinitionHandler(dev, true, false));

        ContextHandler contextDefState = new ContextHandler("/defstate");
        contextDefState.setHandler(new DefinitionHandler(dev, false, true));

        ContextHandler contextDefObjects = new ContextHandler("/defobjects");
        contextDefObjects.setHandler(new DefinitionHandler(dev, true, true));
*/
            //ContextHandler contextReplay = new ContextHandler("/replay");
            //contextReplay.setHandler(new ReplayHandler(true, true));

            ContextHandler contextConvert = new ContextHandler("/convert");

            contextConvert.setHandler(sessions);
            sessions.setHandler(new ConvertHandler());

            ContextHandler contextUavoDb = new ContextHandler("/uavodb");
            contextUavoDb.setHandler(new UAVODatabaseUpdateHandler());

            ContextHandlerCollection contexts = new ContextHandlerCollection();
            contexts.setHandlers(new Handler[]{context,
                    //contextSettings, contextState, contextObjects,
                    //contextDefObjects, contextDefSettings, contextDefState,
                   // contextReplay,
                    contextConvert,
                    contextUavoDb
            });

            server.setHandler(contexts);
            server.start();
            server.join();
        }
    }
}
