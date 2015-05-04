/**
 * Copyright 2013 Sean Kavanagh - sean.p.kavanagh6@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.keybox.manage.task;

import com.google.gson.Gson;
import com.keybox.manage.model.SessionOutput;
import com.keybox.manage.util.DBUtils;
import com.keybox.manage.util.SessionOutputAudit;
import com.keybox.manage.util.SessionOutputUtil;

import javax.websocket.Session;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * class to send output to web socket client
 */
public class SentOutputTask implements Runnable {


    Session session;
    Long sessionId;

    public SentOutputTask(Long sessionId, Session session) {
        this.sessionId = sessionId;
        this.session = session;

    }

    public void run() {

        Connection con = DBUtils.getConn();
        List<StringBuilder> inputLine = new ArrayList<StringBuilder>();
        inputLine.add(new StringBuilder());		// 0	-	LastCommand
        inputLine.add(new StringBuilder());		// 1	-	BEL ( TAB ), strgR, when active
        inputLine.add(new StringBuilder());		// 2	-	Last Login message from start
        inputLine.add(new StringBuilder());		// 3	-	prompt
        inputLine.add(new StringBuilder());		// 4	-	Coded position of the cursor in LastCommand
        SessionOutputAudit audit = new SessionOutputAudit();


        while (session.isOpen()) {
            List<SessionOutput> outputList = SessionOutputUtil.getOutput(con, sessionId, inputLine, audit);
            try {
                if (outputList != null && !outputList.isEmpty()) {
                    String json = new Gson().toJson(outputList);
                    //send json to session
                    this.session.getBasicRemote().sendText(json);
                }
                Thread.sleep(50);
            } catch (Exception ex) {
                ex.printStackTrace();
            }


        }

        DBUtils.closeConn(con);
    }
}
