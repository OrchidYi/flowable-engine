/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flowable.engine.impl.cmd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.jobexecutor.ProcessInstanceMigrationValidationJobHandler;
import org.flowable.engine.impl.migration.ProcessInstanceMigrationValidationResult;
import org.flowable.engine.impl.persistence.entity.ProcessMigrationBatchEntity;
import org.flowable.engine.impl.persistence.entity.ProcessMigrationBatchEntityManager;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.runtime.ProcessMigrationBatch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Dennis Federico
 */
public class GetProcessInstanceMigrationBatchValidationResultCmd implements Command<List<ProcessInstanceMigrationValidationResult>> {

    protected String batchId;

    public GetProcessInstanceMigrationBatchValidationResultCmd(String batchId) {
        this.batchId = batchId;
    }

    @Override
    public List<ProcessInstanceMigrationValidationResult> execute(CommandContext commandContext) {

        ProcessMigrationBatchEntityManager batchManager = CommandContextUtil.getProcessEngineConfiguration(commandContext).getProcessMigrationBatchEntityManager();
        ProcessMigrationBatchEntity batch = batchManager.findById(batchId);

        List<ProcessInstanceMigrationValidationResult> result = new ArrayList<>();

        //TODO WIP - Allow partial results?
        if (batch.isCompleted()) {
            //If batch parent has a result, it doesn't not have children
            if (batch.getResult() != null) {
                result.add(getResultFromJsonString(batch.getResult(), commandContext));
            }

            if (batch.getBatchChildren() != null) {
                batch.getBatchChildren()
                    .stream()
                    .filter(child -> child.getResult() != null)
                    .map(ProcessMigrationBatch::getResult)
                    .map(jsonString -> getResultFromJsonString(jsonString, commandContext))
                    .forEach(result::add);
            }
            return result;
        }
        return null;
    }

    protected ProcessInstanceMigrationValidationResult getResultFromJsonString(String result, CommandContext commandContext) {
        ObjectMapper objectMapper = CommandContextUtil.getProcessEngineConfiguration(commandContext).getObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(result);
            String processInstanceId = null;
            List<String> messages = null;
            if (jsonNode != null) {
                if (jsonNode.has(ProcessInstanceMigrationValidationJobHandler.RESULT_LABEL_PROCESS_INSTANCE_ID)) {
                    processInstanceId = jsonNode.get(ProcessInstanceMigrationValidationJobHandler.RESULT_LABEL_PROCESS_INSTANCE_ID).asText();
                }
                if (jsonNode.has(ProcessInstanceMigrationValidationJobHandler.RESULT_LABEL_VALIDATION_MESSAGES)) {
                    JsonNode arrayNode = jsonNode.get(ProcessInstanceMigrationValidationJobHandler.RESULT_LABEL_VALIDATION_MESSAGES);
                    messages = objectMapper.convertValue(arrayNode, new TypeReference<List<String>>() {

                    });
                }
            }
            if (processInstanceId != null || messages != null) {
                return new ProcessInstanceMigrationValidationResult()
                    .setProcessInstanceId(processInstanceId)
                    .addValidationMessages(messages);
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

}
