package com.dotcms.rendering;

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;

import com.dotcms.business.CloseDB;
import com.dotcms.enterprise.LicenseUtil;
import com.dotcms.enterprise.license.LicenseLevel;
import com.dotcms.rendering.velocity.viewtools.RequestWrapper;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.filters.Constants;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.PageMode;


public class PageRenderModeServlet extends HttpServlet {


    private static final long serialVersionUID = 1L;

    @Override
    @CloseDB
    protected final void service(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        RequestWrapper request = new RequestWrapper(req);
        final String uri = URLDecoder.decode((request.getAttribute(Constants.CMS_FILTER_URI_OVERRIDE) != null)
                ? (String) request.getAttribute(Constants.CMS_FILTER_URI_OVERRIDE)
                : request.getRequestURI(), "UTF-8");

        if (uri == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "PageRenderModeServlet called without running through the CMS Filter");
            Logger.error(this.getClass(),
                    "You cannot call the PageRenderModeServlet without passing the requested url via a  requestAttribute called  "
                            + Constants.CMS_FILTER_URI_OVERRIDE);
            return;
        }

        
        if(needsLicense()) {
            Logger.error(this, "Enterprise License is required for this database and/or app server");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }


        final boolean comeFromSomeWhere = request.getHeader("referer") != null;

        if (APILocator.getLoginServiceAPI().isLoggedIn(request) && !comeFromSomeWhere){
            goToEditPage(uri, response);
            return;
        } 



        request.setRequestUri(uri);
        final PageMode mode = PageMode.getWithNavigateMode(request);

        try {
            RenderModeHandler.modeHandler(mode, request, response).serve();
        } catch (ResourceNotFoundException rnfe) {
            Logger.error(this, "ResourceNotFoundException" + rnfe.toString(), rnfe);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (ParseErrorException pee) {
            Logger.error(this, "Template Parse Exception : " + pee.toString(), pee);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Template Parse Exception");
        } catch (MethodInvocationException mie) {
            Logger.error(this, "MethodInvocationException" + mie.toString(), mie);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "MethodInvocationException Error on template");
        } catch (Exception e) {
            Logger.error(this, "Exception" + e.toString(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Exception Error on template");
        }
        
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        Logger.info(this.getClass(), "Initing VelocityServlet");
    }

    private void goToEditPage(final String requestURI, final HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        final String url = String.format("/dotAdmin/#/edit-page/content?url=%s", requestURI);
        response.sendRedirect(url);
    }

    
    
    private boolean needsLicense() {
        return  ((DbConnectionFactory.isMsSql() && LicenseUtil.getLevel() < LicenseLevel.PROFESSIONAL.level) ||
                (DbConnectionFactory.isOracle() && LicenseUtil.getLevel() < LicenseLevel.PRIME.level) ||
                (!LicenseUtil.isASAllowed())) ;
    }
    
    
}
