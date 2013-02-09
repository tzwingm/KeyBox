<%
/**
 * Copyright (c) 2013 Sean Kavanagh - sean.p.kavanagh6@gmail.com
 * Licensed under the MIT license: http://www.opensource.org/licenses/mit-license.php
 */
%>
<%@ taglib prefix="s" uri="/struts-tags" %>
<!DOCTYPE html>
<html>
<head>

    <jsp:include page="../_res/inc/header.jsp"/>

    <script type="text/javascript">
        $(document).ready(function() {

            $("#add_dialog").dialog({
                autoOpen: false,
                height: 150,
                width: 400,
                modal: true
            });

            //open add dialog
            $("#add_btn").button().click(function() {
                $("#add_dialog").dialog("open");
            });
            //call delete action
            $(".del_btn").button().click(function() {
                var id = $(this).attr('id').replace("del_btn_", "");
                window.location = 'deleteProfileFromUser.action?user.id=<s:property value="user.id"/>&profileId=' + id;
            });

            //submit add or edit form
            $(".submit_btn").button().click(function() {
                $(this).prev().submit();
            });
            //close all forms
            $(".cancel_btn").button().click(function() {
                $("#add_dialog").dialog("close");
                $(".edit_dialog").dialog("close");
            });

            $('.scrollableTable').tableScroll({height:400});
            $(".scrollableTable tr:odd").css("background-color", "#e0e0e0");
        });
    </script>

    <title>KeyBox - Grant System Access</title>

</head>
<body>

<div class="page">
    <jsp:include page="../_res/inc/navigation.jsp"/>

    <div class="content">
        <h3>Grant System Access</h3>
             <p>Add / Delete profiles to be assigned to the current user.</p>

        <h4><s:property value="user.lastNm"/>,&nbsp;<s:property value="user.firstNm"/>
            &nbsp;( <s:property value="user.email"/> )

        </h4>



        <s:if test="user.profileList!= null && !user.profileList.isEmpty()">

            <table class="vborder scrollableTable">
    <thead>

                <tr>
                    <th>Profile Name</th>
                    <th>&nbsp;</th>
                </tr>
    </thead>
                <tbody>



                <s:iterator var="profile" value="user.profileList" status="stat">
                    <tr>


                        <td>
                            <a href="viewProfileSystems.action?profile.id=<s:property value="id"/>" title="Manage Systems in Profile">
                                <s:property value="nm"/>
                            </a>
                        </td>

                        <td>
                            <div id="del_btn_<s:property value="id"/>" class="del_btn">Delete</div>

                        </td>


                    </tr>


                </s:iterator>
                </tbody>
            </table>
        </s:if>

        <div id="add_btn">Add Profile</div>

        <div id="add_dialog" title="Add Profile">
            <s:form action="addProfileToUser">
                <s:select name="profileId" list="profileList" headerKey="" headerValue="- Select One -"
                          listKey="id" listValue="%{nm}"/>
                <s:hidden name="user.id"/>
            </s:form>


            <div class="submit_btn">Submit</div>
            <div class="cancel_btn">Cancel</div>
        </div>
    </div>
</div>
</body>
</html>
