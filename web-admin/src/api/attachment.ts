import { http, type ApiResponse } from "./http";
export interface AttachmentItem {
  attachmentId: string;
  businessType: string;
  businessId: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  uploadedBy: string;
  uploadedAt: string;
}
const key = () => ({ "X-Idempotency-Key": crypto.randomUUID() });
export async function getAttachments(businessType: string, businessId: string) {
  return (
    await http.get<ApiResponse<AttachmentItem[]>>("/attachments", {
      params: { businessType, businessId },
    })
  ).data.data;
}
export async function uploadAttachment(
  businessType: string,
  businessId: string,
  file: File,
) {
  const form = new FormData();
  form.append("businessType", businessType);
  form.append("businessId", businessId);
  form.append("file", file);
  return (
    await http.post<ApiResponse<AttachmentItem>>("/attachments", form, {
      headers: { ...key(), "Content-Type": "multipart/form-data" },
    })
  ).data.data;
}
export async function downloadAttachment(item: AttachmentItem) {
  const r = await http.get(`/attachments/${item.attachmentId}/content`, {
    responseType: "blob",
  });
  const url = URL.createObjectURL(r.data);
  const a = document.createElement("a");
  a.href = url;
  a.download = item.fileName;
  a.click();
  URL.revokeObjectURL(url);
}
