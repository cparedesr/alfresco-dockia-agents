package com.cparedesr.dockia.agents.service.registry.impl;

import com.cparedesr.dockia.agents.model.AgentDeployRequest;
import com.cparedesr.dockia.agents.model.AgentSummary;
import com.cparedesr.dockia.agents.service.exception.BadRequestException;
import com.cparedesr.dockia.agents.service.registry.AgentRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.QName;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

public class AgentRegistryRepoService implements AgentRegistryService {

    private static final String AI_URI = "http://www.cparedesr.com/model/aiagents/1.0";

    private static final QName TYPE_AGENT = QName.createQName(AI_URI, "agent");
    private static final QName PROP_AGENT_ID = QName.createQName(AI_URI, "agentId");
    private static final QName PROP_NAME = QName.createQName(AI_URI, "name");
    private static final QName PROP_IMAGE = QName.createQName(AI_URI, "image");
    private static final QName PROP_TARGET = QName.createQName(AI_URI, "targetNodeId");
    private static final QName PROP_DESIRED = QName.createQName(AI_URI, "desiredState");
    private static final QName PROP_CURRENT = QName.createQName(AI_URI, "currentState");
    private static final QName PROP_HEALTH = QName.createQName(AI_URI, "health");
    private static final QName PROP_CONTAINER = QName.createQName(AI_URI, "containerId");
    private static final QName PROP_CONFIG = QName.createQName(AI_URI, "configJson");
    private static final QName PROP_CREATED = QName.createQName(AI_URI, "createdAt");
    private static final QName PROP_UPDATED = QName.createQName(AI_URI, "updatedAt");

    private NodeService nodeService;
    private SearchService searchService;
    private FileFolderService fileFolderService;

    private final ObjectMapper mapper = new ObjectMapper();

    public void setNodeService(NodeService nodeService) { this.nodeService = nodeService; }
    public void setSearchService(SearchService searchService) { this.searchService = searchService; }
    public void setFileFolderService(FileFolderService fileFolderService) { this.fileFolderService = fileFolderService; }

    @Override
    public boolean existsByName(String name) {
        ResultSet rs = null;
        try {
            SearchParameters sp = new SearchParameters();
            sp.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
            sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
            sp.setQuery("TYPE:\"ai:agent\" AND =ai:name:\"" + escape(name) + "\"");
            rs = searchService.query(sp);
            return rs != null && rs.length() > 0;
        } finally {
            if (rs != null) rs.close();
        }
    }

    @Override
    public void createAgentNode(String agentId,
                               AgentDeployRequest sanitizedRequest,
                               String containerId,
                               String desired,
                               String current,
                               String targetNodeId) {

        try {
            NodeRef folder = ensureRegistryFolder();

            Map<QName, Serializable> props = new HashMap<>();
            props.put(PROP_AGENT_ID, agentId);
            props.put(PROP_NAME, sanitizedRequest.getName());
            props.put(PROP_IMAGE, sanitizedRequest.getImage());
            props.put(PROP_TARGET, targetNodeId);
            props.put(PROP_DESIRED, desired);
            props.put(PROP_CURRENT, current);
            props.put(PROP_CONTAINER, containerId);
            props.put(PROP_CONFIG, mapper.writeValueAsString(sanitizedRequest));
            Date now = new Date();
            props.put(PROP_CREATED, now);
            props.put(PROP_UPDATED, now);

            String fileName = sanitizedRequest.getName() + ".json";
            FileInfo fi = fileFolderService.create(folder, fileName, ContentModel.TYPE_CONTENT);

            NodeRef node = fi.getNodeRef();
            nodeService.setType(node, TYPE_AGENT);
            nodeService.addAspect(node, ContentModel.ASPECT_TITLED, Map.of(
                    ContentModel.PROP_TITLE, sanitizedRequest.getName()
            ));
            nodeService.setProperties(node, props);

        } catch (Exception e) {
            throw new BadRequestException("REGISTRY_WRITE_FAILED", "Failed to persist agent in repository");
        }
    }

    @Override
    public List<AgentSummary> listAgents(int skipCount, int maxItems) {
        int safeSkip = Math.max(0, skipCount);
        int safeMax = (maxItems <= 0) ? 100 : Math.min(maxItems, 500);

        ResultSet rs = null;
        try {
            // Listamos desde el folder /Data Dictionary/AI Agents para evitar devolver cosas fuera del registry.
            NodeRef folder = ensureRegistryFolder();
            String folderPathQuery = buildNodePathQuery(folder);

            SearchParameters sp = new SearchParameters();
            sp.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
            sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
            sp.setQuery("TYPE:\"ai:agent\" AND PATH:\"" + folderPathQuery + "/*\"");
            sp.setSkipCount(safeSkip);
            sp.setMaxItems(safeMax);

            rs = searchService.query(sp);
            if (rs == null || rs.length() == 0) return Collections.emptyList();

            List<AgentSummary> out = new ArrayList<>(rs.length());
            for (int i = 0; i < rs.length(); i++) {
                NodeRef nr = rs.getNodeRef(i);
                out.add(mapSummary(nr));
            }
            return out;

        } finally {
            if (rs != null) rs.close();
        }
    }

    private AgentSummary mapSummary(NodeRef nodeRef) {
        AgentSummary s = new AgentSummary();
        s.setNodeId(nodeRef.getId());

        Serializable agentId = nodeService.getProperty(nodeRef, PROP_AGENT_ID);
        Serializable name = nodeService.getProperty(nodeRef, PROP_NAME);
        Serializable image = nodeService.getProperty(nodeRef, PROP_IMAGE);
        Serializable desired = nodeService.getProperty(nodeRef, PROP_DESIRED);
        Serializable current = nodeService.getProperty(nodeRef, PROP_CURRENT);
        Serializable health = nodeService.getProperty(nodeRef, PROP_HEALTH);
        Serializable container = nodeService.getProperty(nodeRef, PROP_CONTAINER);
        Serializable target = nodeService.getProperty(nodeRef, PROP_TARGET);

        s.setAgentId(agentId != null ? agentId.toString() : null);
        s.setName(name != null ? name.toString() : null);
        s.setImage(image != null ? image.toString() : null);
        s.setDesiredState(desired != null ? desired.toString() : null);
        s.setCurrentState(current != null ? current.toString() : null);
        s.setHealth(health != null ? health.toString() : null);
        s.setContainerId(container != null ? container.toString() : null);
        s.setTargetNodeId(target != null ? target.toString() : null);

        s.setCreatedAt(toIso(nodeService.getProperty(nodeRef, PROP_CREATED)));
        s.setUpdatedAt(toIso(nodeService.getProperty(nodeRef, PROP_UPDATED)));

        return s;
    }

    private String toIso(Serializable v) {
        if (v == null) return null;
        if (v instanceof Date) {
            // ISO básico
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            return sdf.format((Date) v);
        }
        return v.toString();
    }

    private NodeRef ensureRegistryFolder() {
        NodeRef dataDictionary = getDataDictionary();
        return ensureChildFolder(dataDictionary, "AI Agents");
    }

    private NodeRef ensureChildFolder(NodeRef parent, String name) {
        List<FileInfo> children = fileFolderService.list(parent);
        for (FileInfo fi : children) {
            if (fi.isFolder() && name.equals(fi.getName())) return fi.getNodeRef();
        }
        return fileFolderService.create(parent, name, ContentModel.TYPE_FOLDER).getNodeRef();
    }

    private NodeRef getDataDictionary() {
        ResultSet rs = null;
        try {
            SearchParameters sp = new SearchParameters();
            sp.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
            sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
            sp.setQuery("PATH:\"/app:company_home/app:dictionary\"");
            rs = searchService.query(sp);
            if (rs == null || rs.length() == 0) throw new BadRequestException("PATH_NOT_FOUND", "Data Dictionary not found");
            return rs.getNodeRef(0);
        } finally {
            if (rs != null) rs.close();
        }
    }

    /**
     * Devuelve un PATH query fragment seguro para usar en FTS.
     * Ej: /app:company_home/app:dictionary/cm:AI_x0020_Agents (depende de nombres)
     * Para simplificar sin ContentService, usamos NodeRef -> nodeService.getPath().
     */
    private String buildNodePathQuery(NodeRef nodeRef) {
        // Path en formato /{...}/{...}
        // Alfresco FTS espera PATH:"/app:company_home/..."
        // nodeService.getPath(nodeRef).toPrefixString(namespaceService) sería ideal,
        // pero aquí evitamos meter NamespaceService: usamos cm:name encoding típico.
        // Como mínimo, funciona bien si el folder se llama exactamente "AI Agents" creado por FileFolderService.
        // Ruta fija:
        return "/app:company_home/app:dictionary/cm:AI_x0020_Agents";
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}