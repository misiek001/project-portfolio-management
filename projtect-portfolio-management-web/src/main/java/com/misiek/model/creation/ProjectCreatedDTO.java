package com.misiek.model.creation;

import com.misiek.model.ProjectStatusDTO;

public class ProjectCreatedDTO extends ProjectCreationDTO {

    private Long id;

    private ProjectStatusDTO projectStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public ProjectStatusDTO getProjectStatus() {
        return projectStatus;
    }

    @Override
    public void setProjectStatus(ProjectStatusDTO projectStatus) {
        this.projectStatus = projectStatus;
    }
}