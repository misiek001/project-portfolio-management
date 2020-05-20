package com.mbor.service;

import com.mbor.dao.IProjectDao;
import com.mbor.domain.*;
import com.mbor.domain.employeeinproject.*;
import com.mbor.domain.projectaspect.ProjectAspectLine;
import com.mbor.exception.NoSetProjectManagerException;
import com.mbor.exception.ProjectRoleAlreadyExistException;
import com.mbor.exception.WrongProjectManagerException;
import com.mbor.mapper.ProjectAspectLineMapper;
import com.mbor.mapper.ProjectMapper;
import com.mbor.mapper.RealEndDateMapper;
import com.mbor.model.*;
import com.mbor.model.assignment.EmployeeAssignDTO;
import com.mbor.model.creation.ProjectCreatedDTO;
import com.mbor.model.creation.ProjectCreationDTO;
import com.mbor.model.projectaspect.ProjectAspectLineDTO;
import com.mbor.model.search.ResourceManagerSearchProjectDTO;
import com.mbor.model.search.SearchProjectDTO;
import com.mbor.model.search.SupervisorSearchProjectDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.mbor.service.ServiceUtils.tryCast;

@Service
@Transactional
public class ProjectService extends RawService<Project> implements IProjectService {

    private final IProjectDao projectDao;

    private final IEmployeeService employeeService;
    private final IBusinessUnitService businessUnitService;
    private final IProjectRoleService projectRoleService;

    private final ProjectMapper projectMapper;
    private final ProjectAspectLineMapper projectAspectMapper;
    private final RealEndDateMapper realEndDateMapper;

    @Autowired
    public ProjectService(IProjectDao projectDao, IEmployeeService employeeService, IBusinessUnitService businessUnitService, IProjectRoleService projectRoleService, ProjectMapper projectMapper, ProjectAspectLineMapper projectAspectMapper, RealEndDateMapper realEndDateMapper) {
        this.projectDao = projectDao;
        this.employeeService = employeeService;
        this.businessUnitService = businessUnitService;
        this.projectRoleService = projectRoleService;
        this.projectMapper = projectMapper;
        this.projectAspectMapper = projectAspectMapper;
        this.realEndDateMapper = realEndDateMapper;
    }

    @Override
    public List<ProjectDTO> findAll() {
        List<Project> projects =  super.findAllInternal();
        return projects.stream()
                .map(projectMapper::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    public ProjectDTO find(Long id) {
        return projectMapper.convertToDto(findInternal(id));
    }

    @Override
    public ProjectCreatedDTO save(ProjectCreationDTO projectCreationDTO) {
        Project project = projectMapper.convertCreationDtoToEntity(projectCreationDTO);
        saveInternal(project);
        ProjectStatusHistoryLine projectStatusHistoryLine = new ProjectStatusHistoryLine();
        projectStatusHistoryLine.setPreviousStatus(ProjectStatus.ANALYSIS);
        projectStatusHistoryLine.setCurrentStatus(ProjectStatus.ANALYSIS);
        projectStatusHistoryLine.setDescription("Project opened");
        project.addProjectStatusHistoryLine(projectStatusHistoryLine);
        project.setBusinessRelationManager((BusinessRelationManager) employeeService.findInternal(projectCreationDTO.getBusinessRelationManagerId()));
        project.setBusinessLeader((BusinessLeader) projectRoleService.findInternal(projectCreationDTO.getBusinessLeaderId()));
        project.setPrimaryBusinessUnit(businessUnitService.findInternal(projectCreationDTO.getPrimaryBusinessUnitId()));


        return projectMapper.convertEntityToCreatedDto(project);
    }

    @Override
    public void delete(Long id) {
        Project project = findInternal(id);
        if (project.getProjectManager() != null) {
            project.getProjectManager().getProjects().remove(project);
        }
        if(project.getResourceManager() != null){
            project.getResourceManager().getProjects().remove(project);
        }
        if(project.getBusinessRelationManager() != null){
            project.getBusinessRelationManager().getProjects().remove(project);
        }
        project.getSolutionArchitects().forEach(solutionArchitect -> solutionArchitect
                .getProjects()
                .remove(project));
        updateInternal(project);
        deleteInternal(id);
    }

    @Override
    public ProjectDTO updateProjectAspects(Long projectId, ProjectAspectLineDTO projectAspectLineDTO, Long projectManagerId) {
        Project project = getProjectDedicatedToProjectManager(projectId, projectManagerId);
        ProjectAspectLine projectAspectLine = projectAspectMapper.convertToEntity(projectAspectLineDTO);
        project.addProjectAspectLine(projectAspectLine);
        return projectMapper.convertToDto(updateInternal(project));
    }

    @Override
    public ProjectDTO addProjectEndDate(Long projectId, RealEndDateDTO realEndDateDTO, Long projectManagerId){
        Project project = getProjectDedicatedToProjectManager(projectId, projectManagerId);
        RealEndDate realEndDate = realEndDateMapper.convertToEntity(realEndDateDTO);
        project.addRealEndDate(realEndDate);

    return projectMapper.convertToDto(updateInternal(project));
    }

    private Project getProjectDedicatedToProjectManager(Long projectId, Long projectManagerId) {
        Project project = findInternal(projectId);
        if (project.getProjectManager() == null) {
            throw new NoSetProjectManagerException("No Project Manager assigned to project with id:" + project.getId());
        }
        if (!project.getProjectManager().getId().equals(projectManagerId)) {
            throw new WrongProjectManagerException("Project with id:" + project.getId() + " has projectManagerWith id: " + project.getProjectManager().getId() + " ,not:" + projectManagerId);
        }
        return project;
    }

    @Override
    public List<ProjectDTO> findByMultipleCriteria(SearchProjectDTO searchProjectDTO) {
        String projectName = searchProjectDTO.getProjectName();
        List<ProjectClass> projectClass = null;
        if (searchProjectDTO.getProjectClassDTOList() != null) {
            projectClass = searchProjectDTO.getProjectClassDTOList()
                    .stream()
                    .map(mapProjectClassDTOToProjectClass())
                    .collect(Collectors.toList());
        }
        String businessUnitName = searchProjectDTO.getBusinessUnitName();
        List<ProjectStatus> projectStatusList = null;
        if (searchProjectDTO.getProjectStatusDTOList() != null) {
            projectStatusList = searchProjectDTO.getProjectStatusDTOList()
                    .stream()
                    .map(mapProjectStatusDTOToProjectStatus())
                    .collect(Collectors.toList());
        }
        LocalDate projectStartDate = searchProjectDTO.getProjectStartDateLaterThat();
        List<Project> foundProject = getDao().findByMultipleCriteria(projectName, projectClass, businessUnitName, projectStatusList, projectStartDate);
        List<ProjectDTO> projectDTOList = new ArrayList<>();
        foundProject.forEach(project -> projectDTOList.add(projectMapper.convertToDto(project)));
        return projectDTOList;
    }

    @Override
    public List<ProjectDTO> findResourceManagerProjects(Long resourceManagerId, ResourceManagerSearchProjectDTO resourceManagerSearchProjectDTO){
        Long projectId = resourceManagerSearchProjectDTO.getProjectId();
        String projectName = resourceManagerSearchProjectDTO.getProjectName();
        List<ProjectClass> projectClass = null;
        if (resourceManagerSearchProjectDTO.getProjectClassDTOList() != null) {
            projectClass = resourceManagerSearchProjectDTO.getProjectClassDTOList()
                    .stream()
                    .map(mapProjectClassDTOToProjectClass())
                    .collect(Collectors.toList());
        }
        List<ProjectStatus> projectStatusList = null;
        if (resourceManagerSearchProjectDTO.getProjectStatusDTOList() != null) {
            projectStatusList = resourceManagerSearchProjectDTO.getProjectStatusDTOList()
                    .stream()
                    .map(mapProjectStatusDTOToProjectStatus())
                    .collect(Collectors.toList());
        }
        List<Project> foundProject = getDao().findResourceManagerProjects(resourceManagerId, projectId, projectName, projectClass, projectStatusList);
        List<ProjectDTO> projectDTOList = new ArrayList<>();
        foundProject.forEach(project -> projectDTOList.add(projectMapper.convertToDto(project)));
        return projectDTOList;
    }

    @Override
    public List<ProjectDTO> findSupervisorProjects(Long supervisorId, SupervisorSearchProjectDTO supervisorSearchProjectDTO){
        Long projectId = supervisorSearchProjectDTO.getProjectId();
        String projectName = supervisorSearchProjectDTO.getProjectName();
        List<ProjectClass> projectClass = null;
        if (supervisorSearchProjectDTO.getProjectClassDTOList() != null) {
            projectClass = supervisorSearchProjectDTO.getProjectClassDTOList()
                    .stream()
                    .map(mapProjectClassDTOToProjectClass())
                    .collect(Collectors.toList());
        }
        List<ProjectStatus> projectStatusList = null;
        if (supervisorSearchProjectDTO.getProjectStatusDTOList() != null) {
            projectStatusList = supervisorSearchProjectDTO.getProjectStatusDTOList()
                    .stream()
                    .map(mapProjectStatusDTOToProjectStatus())
                    .collect(Collectors.toList());
        }
        List<Long> projectManagerIdList = null;
        if (supervisorSearchProjectDTO.getProjectManagerDTOList() != null) {
            projectManagerIdList = supervisorSearchProjectDTO.getProjectManagerDTOList()
                    .stream()
                    .map(IdDTO::getId)
                    .collect(Collectors.toList());
        }
        List<Long> solutionArchitectsIdList = null;
        if (supervisorSearchProjectDTO.getSolutionArchitectDTOList() != null) {
            solutionArchitectsIdList = supervisorSearchProjectDTO.getSolutionArchitectDTOList()
                    .stream()
                    .map(IdDTO::getId)
                    .collect(Collectors.toList());
        }
        List<Project> foundProject = getDao().findSupervisorProjects(supervisorId, projectId, projectName, projectClass, projectStatusList, projectManagerIdList, solutionArchitectsIdList);
        List<ProjectDTO> projectDTOList = new ArrayList<>();
        foundProject.forEach(project -> projectDTOList.add(projectMapper.convertToDto(project)));
        return projectDTOList;
    }

    @Override
    public List<ProjectDTO> findConsultantProjects(Long consultantId){
        List<Project> foundProject = getDao().findConsultantProject(consultantId);
        List<ProjectDTO> projectDTOList = new ArrayList<>();
        foundProject.forEach(project -> projectDTOList.add(projectMapper.convertToDto(project)));
        return projectDTOList;
    }

    @Override
    public ProjectDTO assignEmployee(Long projectId, EmployeeAssignDTO employeeAssignDTO) {
        Project project = findInternal(projectId);
        if (employeeAssignDTO.getProjectManagerId() != null) {
            ProjectManager  projectManager = tryCast(ProjectManager.class, projectRoleService.findInternal(employeeAssignDTO.getProjectManagerId()));
            project.setProjectManager(projectManager);
            }
        if (employeeAssignDTO.getBusinessRelationManagerId() != null) {
            project.setBusinessRelationManager(tryCast(BusinessRelationManager.class, employeeService.find(employeeAssignDTO.getBusinessRelationManagerId())));
        }
        if (employeeAssignDTO.getResourceManagerId() != null) {
            ResourceManager resourceManager = tryCast(ResourceManager.class, projectRoleService.findInternal(employeeAssignDTO.getResourceManagerId()));
            project.setResourceManager(resourceManager);
        }
        if (!employeeAssignDTO.getSolutionArchitectIdSet().isEmpty()) {
            employeeAssignDTO.getSolutionArchitectIdSet().forEach(solutionArchitectId -> {
                project.addSolutionArchitect(tryCast(SolutionArchitect.class, projectRoleService.find(solutionArchitectId)));
            });
        }
        if (employeeAssignDTO.getBusinessLeaderId() != null) {
            BusinessLeader   businessLeader = tryCast(BusinessLeader.class, projectRoleService.findInternal(employeeAssignDTO.getBusinessLeaderId()));
            project.setBusinessLeader(businessLeader);
        }
        return projectMapper.convertToDto(updateInternal(project));
    }

    private Function<ProjectClassDTO, ProjectClass> mapProjectClassDTOToProjectClass() {
        return projectClassDTO -> Enum.valueOf(ProjectClass.class, projectClassDTO.name());
    }

    private Function<ProjectStatusDTO, ProjectStatus> mapProjectStatusDTOToProjectStatus() {
        return projectStatusDTO -> Enum.valueOf(ProjectStatus.class, projectStatusDTO.name());
    }

    private  <T extends ProjectRole> void  checkIfRoleAlreadyExist(Class<T> clazz, Long employeeId){
        List<ProjectRole> roles = projectRoleService.findAllRoleOfEmployee(employeeId);
        roles.forEach( role -> {
            if (clazz.isInstance(role)) {
                throw new ProjectRoleAlreadyExistException("Employee with id:" + employeeId + "already has a role:" + clazz.getName());
            }
        });
    }

    @Override
    public IProjectDao getDao() {
        return projectDao;
    }
}

