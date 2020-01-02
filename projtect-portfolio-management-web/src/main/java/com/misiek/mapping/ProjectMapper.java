package com.misiek.mapping;

import com.misiek.domain.Project;
import com.misiek.model.ProjectDTO;
import com.misiek.model.creation.ProjectCreatedDTO;
import com.misiek.model.creation.ProjectCreationDTO;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper extends Mapper<ProjectDTO, Project> {

    private final BusinessLeaderMapper businessLeaderMapper;
    private final BusinessRelationManagerMapper businessRelationManagerMapper;

    public ProjectMapper(ModelMapper modelMapper, BusinessLeaderMapper businessLeaderMapper, BusinessRelationManagerMapper businessRelationManagerMapper) {
        super(modelMapper);
        this.businessLeaderMapper = businessLeaderMapper;
        this.businessRelationManagerMapper = businessRelationManagerMapper;
    }

    @Override
    public ProjectDTO convertToDto(Project project) {
        return null;
    }

    @Override
    public Project convertToEntity(ProjectDTO projectDTO) {
        return null;
    }

    public Project mapProjectCreationDTOtoProject(ProjectCreationDTO projectCreationDTO) {
        return  modelMapper.map(projectCreationDTO, Project.class);

    }

    public ProjectCreatedDTO mapCreatedProjectToDTO(Project project){
        return modelMapper.map(project, ProjectCreatedDTO.class);
    }
}